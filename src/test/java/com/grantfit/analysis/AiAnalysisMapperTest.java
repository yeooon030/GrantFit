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
				null, null, null, null, null));

		AiAnalysis saved = aiAnalysisMapper.selectByProgramAndVersion(programId, "v1");
		assertThat(saved.id()).isEqualTo(id);
		assertThat(saved.status()).isEqualTo("COMPLETED");
		assertThat(saved.model()).isEqualTo("gemini-test-001");
		assertThat(saved.promptVersion()).isEqualTo("v1");
		assertThat(saved.eligibilityJson()).contains("\"regions\": [\"서울\"]");
		assertThat(saved.supportAmountJson()).contains("\"maxAmount\": 50000000");
		assertThat(saved.preferenceJson()).isEqualTo("[]");
		assertThat(saved.summary()).isEqualTo("요약");
		assertThat(saved.analyzedAt()).isNotNull();
	}

	@Test
	void 실패는_원인_코드와_메시지를_남긴다() {
		long id = aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v1");

		aiAnalysisMapper.updateFailed(id, "VALIDATION_ERROR", "evidence 가 원문에 없음");

		AiAnalysis saved = aiAnalysisMapper.selectById(id);
		assertThat(saved.status()).isEqualTo("FAILED");
		assertThat(saved.errorCode()).isEqualTo("VALIDATION_ERROR");
		assertThat(saved.errorMessage()).isEqualTo("evidence 가 원문에 없음");
		assertThat(saved.analyzedAt()).isNull();
	}

	@Test
	void 같은_공고와_버전을_다시_분석하면_같은_행을_비우고_재사용한다() {
		long first = aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v1");
		aiAnalysisMapper.updateFailed(first, "TIMEOUT", "시간 초과");

		long second = aiAnalysisMapper.upsertAnalyzing(programId, "gemini-test", "v1");

		AiAnalysis saved = aiAnalysisMapper.selectById(second);
		assertThat(second).isEqualTo(first);
		assertThat(saved.status()).isEqualTo("ANALYZING");
		assertThat(saved.errorCode()).isNull();
		assertThat(saved.errorMessage()).isNull();
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

		assertThatThrownBy(() -> aiAnalysisMapper.updateFailed(id, "UNKNOWN", "x"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
