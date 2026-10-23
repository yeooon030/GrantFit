package com.grantfit.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.grantfit.program.SupportProgram;
import com.grantfit.program.SupportProgramMapper;

/**
 * 실제 grantfit DB 에 쓴다. @Transactional 로 테스트 종료 시 롤백된다.
 */
@SpringBootTest
@Transactional
class AiAnalysisMapperTest {

	@Autowired
	private AiAnalysisMapper aiAnalysisMapper;

	@Autowired
	private SupportProgramMapper supportProgramMapper;

	private long programId;

	@BeforeEach
	void setUp() {
		supportProgramMapper.upsert(new SupportProgram(null, "TEST_AI_0001", "테스트 공고", null, null, null,
				null, null, null, null, "{}", null, null));
		programId = supportProgramMapper.selectByExternalId("TEST_AI_0001").id();
	}

	@Test
	void ANALYZING으로_만들고_COMPLETED_결과를_JSONB로_저장한다() {
		long id = aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v1");
		assertThat(aiAnalysisMapper.selectById(id).status()).isEqualTo("ANALYZING");

		aiAnalysisMapper.updateCompleted(new AiAnalysis(id, null, "gemini-test-001", null, null,
				"{\"regions\":[\"서울\"],\"conditions\":[]}", "[]", "[\"사업계획서\"]",
				"{\"maxAmount\":50000000,\"selfPaymentRequired\":null}", "[]",
				"{\"regions\":\"서울 소재\"}", "[]", "요약",
				null, null, 2, null, null, null));

		AiAnalysis saved = aiAnalysisMapper.selectByProgramAndVersion(programId, "v1");
		assertThat(saved.id()).isEqualTo(id);
		assertThat(saved.status()).isEqualTo("COMPLETED");
		assertThat(saved.model()).isEqualTo("gemini-test-001");
		assertThat(saved.promptVersion()).isEqualTo("v1");
		assertThat(saved.eligibilityJson()).contains("\"regions\": [\"서울\"]");
		assertThat(saved.supportAmountJson()).contains("\"maxAmount\": 50000000");
		assertThat(saved.preferenceJson()).isEqualTo("[]");
		assertThat(saved.summary()).isEqualTo("요약");
		assertThat(saved.attemptCount()).isEqualTo(2);
		assertThat(saved.analyzedAt()).isNotNull();
	}

	@Test
	void 실패는_원인_코드와_메시지를_남긴다() {
		long id = aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v1");

		aiAnalysisMapper.updateFailed(id, "VALIDATION_ERROR", "evidence 가 원문에 없음", 1);

		AiAnalysis saved = aiAnalysisMapper.selectById(id);
		assertThat(saved.status()).isEqualTo("FAILED");
		assertThat(saved.errorCode()).isEqualTo("VALIDATION_ERROR");
		assertThat(saved.errorMessage()).isEqualTo("evidence 가 원문에 없음");
		assertThat(saved.attemptCount()).isEqualTo(1);
		assertThat(saved.analyzedAt()).isNull();
	}

	@Test
	void 같은_공고와_버전을_다시_분석하면_같은_행을_비우고_재사용한다() {
		long first = aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v1");
		aiAnalysisMapper.updateFailed(first, "TIMEOUT", "시간 초과", 3);

		long second = aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v1");

		AiAnalysis saved = aiAnalysisMapper.selectById(second);
		assertThat(second).isEqualTo(first);
		assertThat(saved.status()).isEqualTo("ANALYZING");
		assertThat(saved.errorCode()).isNull();
		assertThat(saved.errorMessage()).isNull();
		assertThat(saved.attemptCount()).isZero();
	}

	@Test
	void COMPLETED는_덮어쓰지_않고_null을_반환한다() {
		long id = aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v1");
		aiAnalysisMapper.updateCompleted(new AiAnalysis(id, null, "gemini-test", null, null,
				"{}", "[]", "[]", "{}", "[]", "{}", "[]", "요약", null, null, 1, null, null, null));

		assertThat(aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v1")).isNull();
		assertThat(aiAnalysisMapper.selectById(id).status()).isEqualTo("COMPLETED");
	}

	@Test
	void 분석_중인_행은_덮어쓰지_않고_null을_반환한다() {
		long id = aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v1");

		assertThat(aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v1")).isNull();
		assertThat(aiAnalysisMapper.selectById(id).status()).isEqualTo("ANALYZING");
	}

	@Test
	void MILESTONE_3에서_추가한_error_code를_저장할_수_있다() {
		long id = aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v1");

		aiAnalysisMapper.updateFailed(id, "NETWORK_ERROR", "연결 실패", 3);
		assertThat(aiAnalysisMapper.selectById(id).errorCode()).isEqualTo("NETWORK_ERROR");

		aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v1");
		aiAnalysisMapper.updateFailed(id, "INTERNAL_ERROR", "raw_content 손상", 0);
		assertThat(aiAnalysisMapper.selectById(id).errorCode()).isEqualTo("INTERNAL_ERROR");
	}

	@Test
	void 일괄_분석_대상은_COMPLETED가_없는_공고() {
		long id = aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v-batch");
		assertThat(aiAnalysisMapper.selectProgramIdsToAnalyze("v-batch", 1000)).contains(programId);

		aiAnalysisMapper.updateFailed(id, "TIMEOUT", "t", 3);
		assertThat(aiAnalysisMapper.selectProgramIdsToAnalyze("v-batch", 1000)).contains(programId);

		aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v-batch");
		aiAnalysisMapper.updateCompleted(new AiAnalysis(id, null, "gemini-test", null, null,
				"{}", "[]", "[]", "{}", "[]", "{}", "[]", "요약", null, null, 1, null, null, null));
		assertThat(aiAnalysisMapper.selectProgramIdsToAnalyze("v-batch", 1000)).doesNotContain(programId);
	}

	@Test
	void 상태별_건수를_센다() {
		long id = aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v-count");
		aiAnalysisMapper.updateFailed(id, "VALIDATION_ERROR", "x", 1);

		assertThat(aiAnalysisMapper.countByStatus("v-count"))
				.containsExactly(new AiAnalysisMapper.StatusCount("FAILED", "VALIDATION_ERROR", 1));
	}

	@Test
	void 다른_prompt_version은_별도_행() {
		long v1 = aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v1");
		long v2 = aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v2");

		assertThat(v2).isNotEqualTo(v1);
	}

	@Test
	void DDL에_없는_error_code는_거부된다() {
		long id = aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v1");

		assertThatThrownBy(() -> aiAnalysisMapper.updateFailed(id, "UNKNOWN", "x", 1))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
