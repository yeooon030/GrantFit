package com.grantfit.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.grantfit.analysis.AiAnalysis;
import com.grantfit.analysis.AiAnalysisMapper;
import com.grantfit.analysis.AiAnalysisMapper.StatusCount;
import com.grantfit.program.SupportProgram;
import com.grantfit.program.SupportProgramMapper;

class EvaluationServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
	private final SupportProgramMapper programMapper = mock(SupportProgramMapper.class);
	private final AiAnalysisMapper analysisMapper = mock(AiAnalysisMapper.class);

	@TempDir
	Path dir;

	private EvaluationService service;

	@BeforeEach
	void setUp() throws IOException {
		Path golden = dir.resolve("golden.json");
		Files.writeString(golden, """
				{"items":[
				  {"externalId":"A","title":"일치","labeled":true,
				   "regions":["경남"],"businessAge":{"minYears":null,"maxYears":7},"maxAmount":50000000,
				   "applicantTypes":["CORPORATION"],"applicationStart":"2026-10-01","applicationEnd":"2026-10-31"},
				  {"externalId":"B","title":"불일치","labeled":true,
				   "regions":[],"businessAge":{"minYears":null,"maxYears":null},"maxAmount":null,
				   "applicantTypes":[],"applicationStart":null,"applicationEnd":null},
				  {"externalId":"C","title":"미작성","labeled":false},
				  {"externalId":"D","title":"분석 실패","labeled":true,"regions":[]}
				]}
				""");
		service = new EvaluationService(programMapper, analysisMapper, objectMapper, golden.toString());

		program(1L, "A", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
		program(2L, "B", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
		program(4L, "D", null, null);

		when(analysisMapper.selectByProgramAndVersion(1L, "v1")).thenReturn(completed(1L,
				"{\"regions\":[\"경남\"],\"businessAge\":{\"minYears\":null,\"maxYears\":7},\"applicantTypes\":[\"CORPORATION\"],\"conditions\":[]}",
				"{\"maxAmount\":50000000,\"selfPaymentRequired\":null}",
				"{\"regions\":\"김해시 소재\",\"businessAge\":\"창업 7년 이내\",\"applicantTypes\":null,\"support\":\"최대 5천만원\"}"));
		when(analysisMapper.selectByProgramAndVersion(2L, "v1")).thenReturn(completed(2L,
				"{\"regions\":[\"전국\"],\"businessAge\":{\"minYears\":3,\"maxYears\":null},\"applicantTypes\":[],\"conditions\":[]}",
				"{\"maxAmount\":null,\"selfPaymentRequired\":null}",
				"{\"regions\":null,\"businessAge\":\"3년 이상\",\"applicantTypes\":null,\"support\":null}"));
		when(analysisMapper.selectByProgramAndVersion(4L, "v1")).thenReturn(new AiAnalysis(40L, 4L, "m", "v1", "FAILED",
				null, null, null, null, null, null, null, null, "API_ERROR", "503", 3, null, null, null));
		when(analysisMapper.countByStatus("v1")).thenReturn(List.of(
				new StatusCount("COMPLETED", null, 18),
				new StatusCount("FAILED", "VALIDATION_ERROR", 1),
				new StatusCount("FAILED", "INVALID_JSON", 1),
				new StatusCount("FAILED", "API_ERROR", 2)));
	}

	@Test
	void 필드별_정확도와_불일치_목록() {
		EvaluationReport r = service.evaluate("v1");

		assertThat(r.goldenItems()).isEqualTo(4);
		assertThat(r.labeledItems()).isEqualTo(3);
		assertThat(r.evaluatedItems()).isEqualTo(2);
		assertThat(r.notAnalyzed()).containsExactly("D");

		assertThat(r.fields().get("regions")).isEqualTo(new EvaluationReport.Score(1, 2, 0.5));
		assertThat(r.fields().get("businessAge")).isEqualTo(new EvaluationReport.Score(1, 2, 0.5));
		assertThat(r.fields().get("maxAmount")).isEqualTo(new EvaluationReport.Score(2, 2, 1.0));
		assertThat(r.fields().get("applicantTypes")).isEqualTo(new EvaluationReport.Score(2, 2, 1.0));
		assertThat(r.mainFieldAccuracy()).isEqualTo(new EvaluationReport.Score(6, 8, 0.75));

		assertThat(r.mismatches()).extracting(EvaluationReport.Mismatch::externalId, EvaluationReport.Mismatch::field)
				.containsExactlyInAnyOrder(
						org.assertj.core.groups.Tuple.tuple("B", "regions"),
						org.assertj.core.groups.Tuple.tuple("B", "businessAge"),
						org.assertj.core.groups.Tuple.tuple("B", "applicationPeriod(rule)"));
	}

	@Test
	void 신청기간은_Java_파싱_결과와_비교한다() {
		// B: 정답은 null~null 인데 support_program 에는 날짜가 있다
		assertThat(service.evaluate("v1").applicationPeriod()).isEqualTo(new EvaluationReport.Score(1, 2, 0.5));
	}

	@Test
	void Evidence_포함률은_AI가_값을_낸_항목만_센다() {
		// A: regions·businessAge·maxAmount·applicantTypes 추출, applicantTypes 근거 없음 → 3/4
		// B: regions(전국) 근거 없음, businessAge 근거 있음 → 1/2
		assertThat(service.evaluate("v1").evidenceCoverage()).isEqualTo(new EvaluationReport.Score(4, 6, 0.667));
	}

	@Test
	void Structured_Output_성공률은_인프라_실패를_뺀다() {
		EvaluationReport.StructuredOutput so = service.evaluate("v1").structuredOutput();

		assertThat(so.completed()).isEqualTo(18);
		assertThat(so.infraFailures()).isEqualTo(2);
		assertThat(so.rate()).isEqualTo(0.9);
	}

	private void program(long id, String externalId, LocalDate start, LocalDate end) {
		when(programMapper.selectByExternalId(externalId)).thenReturn(new SupportProgram(
				id, externalId, "t", null, null, null, start, end, null, null, "{}", null, null));
	}

	private AiAnalysis completed(long programId, String eligibility, String support, String evidence) {
		return new AiAnalysis(programId * 10, programId, "m", "v1", "COMPLETED",
				eligibility, "[]", "[]", support, "[]", evidence, "[]", "요약", null, null, 1, null, null, null);
	}
}
