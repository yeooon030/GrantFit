package com.grantfit.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantfit.program.SupportProgram;
import com.grantfit.program.SupportProgramMapper;

/**
 * 분석 흐름(재사용 / 성공 / 실패 기록 / 재시도)을 Gemini·DB 없이 검증한다.
 * 재시도 대기 시간은 0 으로 둔다.
 */
class AnalysisServiceTest {

	private static final long PROGRAM_ID = 50L;
	private static final long ANALYSIS_ID = 7L;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final SupportProgramMapper programMapper = mock(SupportProgramMapper.class);
	private final AiAnalysisMapper analysisMapper = mock(AiAnalysisMapper.class);
	private final GeminiClient geminiClient = mock(GeminiClient.class);
	private AnalysisService service;
	private String successText;

	@BeforeEach
	void setUp() throws IOException {
		service = new AnalysisService(programMapper, analysisMapper, geminiClient,
				new AnalysisPrompt(objectMapper), new AnalysisValidator(),
				new AnalysisRetryPolicy(3, Duration.ZERO, Duration.ofSeconds(30)), objectMapper);

		when(programMapper.selectById(PROGRAM_ID)).thenReturn(program(read("/analysis/raw-content-sample.json")));
		when(geminiClient.model()).thenReturn("gemini-3.8-flash");
		when(analysisMapper.upsertAnalyzing(PROGRAM_ID, "gemini-3.8-flash", AnalysisPrompt.VERSION))
				.thenReturn(ANALYSIS_ID);
		when(analysisMapper.selectById(ANALYSIS_ID)).thenReturn(row("COMPLETED"));

		successText = objectMapper.readTree(read("/analysis/gemini-response-success.json"))
				.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
	}

	@Test
	void 같은_prompt_version으로_완료된_결과가_있으면_Gemini를_호출하지_않는다() {
		when(analysisMapper.selectByProgramAndVersion(PROGRAM_ID, AnalysisPrompt.VERSION)).thenReturn(row("COMPLETED"));

		AnalysisService.AnalysisOutcome outcome = service.analyze(PROGRAM_ID);

		assertThat(outcome.reused()).isTrue();
		verify(geminiClient, never()).generate(anyString(), anyString(), any());
		verify(analysisMapper, never()).upsertAnalyzing(anyLong(), anyString(), anyString());
	}

	@Test
	void 실제_응답_샘플은_검증을_통과해_COMPLETED로_저장된다() {
		givenGeminiReturns(successText);

		service.analyze(PROGRAM_ID);

		ArgumentCaptor<AiAnalysis> saved = ArgumentCaptor.forClass(AiAnalysis.class);
		verify(analysisMapper).updateCompleted(saved.capture());
		verify(analysisMapper, never()).updateFailed(anyLong(), anyString(), anyString(), anyInt());
		assertThat(saved.getValue().id()).isEqualTo(ANALYSIS_ID);
		assertThat(saved.getValue().eligibilityJson()).contains("\"regions\":[\"전남광주\"]", "\"conditions\":[");
		assertThat(saved.getValue().summary()).isNotBlank();
		assertThat(saved.getValue().attemptCount()).isEqualTo(1);
	}

	@Test
	void JSON이_아니면_INVALID_JSON으로_기록한다() {
		givenGeminiReturns("{not json");

		service.analyze(PROGRAM_ID);

		verify(analysisMapper).updateFailed(eq(ANALYSIS_ID), eq("INVALID_JSON"), anyString(), eq(1));
		verify(analysisMapper, never()).updateCompleted(any());
		verify(geminiClient, times(1)).generate(anyString(), anyString(), any());
	}

	@Test
	void 스키마에_없는_필드가_있으면_INVALID_JSON() {
		givenGeminiReturns(successText.replaceFirst("\\{", "{\"score\":95,"));

		service.analyze(PROGRAM_ID);

		verify(analysisMapper).updateFailed(eq(ANALYSIS_ID), eq("INVALID_JSON"), anyString(), eq(1));
	}

	@Test
	void 원문에_없는_근거가_있으면_VALIDATION_ERROR와_원인을_기록한다() {
		givenGeminiReturns(successText.replace(
				"\"evidence\": \"광주광역시 관내 사업자등록증을 보유한 중소 제조업체\"",
				"\"evidence\": \"광주에 사업장을 둔 모든 기업\""));

		service.analyze(PROGRAM_ID);

		ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
		verify(analysisMapper).updateFailed(eq(ANALYSIS_ID), eq("VALIDATION_ERROR"), message.capture(), eq(1));
		assertThat(message.getValue()).contains("원문에 없음: 광주에 사업장을 둔 모든 기업");
	}

	@Test
	void 재시도하지_않는_Gemini_오류는_1회만_호출하고_코드_그대로_기록한다() {
		when(geminiClient.generate(anyString(), anyString(), any()))
				.thenThrow(new AnalysisException(AnalysisErrorCode.API_ERROR, "Gemini HTTP 403"));

		service.analyze(PROGRAM_ID);

		verify(geminiClient, times(1)).generate(anyString(), anyString(), any());
		verify(analysisMapper).updateFailed(ANALYSIS_ID, "API_ERROR", "Gemini HTTP 403", 1);
	}

	@Test
	void 일시적_오류_후_성공하면_COMPLETED와_호출_횟수를_기록한다() {
		when(geminiClient.generate(anyString(), anyString(), any()))
				.thenThrow(AnalysisException.retryable(AnalysisErrorCode.TIMEOUT, "시간 초과", null))
				.thenReturn(new GeminiClient.GeminiResponse(successText, "gemini-3.8-flash"));

		service.analyze(PROGRAM_ID);

		ArgumentCaptor<AiAnalysis> saved = ArgumentCaptor.forClass(AiAnalysis.class);
		verify(analysisMapper).updateCompleted(saved.capture());
		assertThat(saved.getValue().attemptCount()).isEqualTo(2);
	}

	@Test
	void 재시도_횟수를_다_쓰면_마지막_오류로_FAILED() {
		when(geminiClient.generate(anyString(), anyString(), any()))
				.thenThrow(AnalysisException.retryable(AnalysisErrorCode.API_ERROR, "Gemini HTTP 503", null))
				.thenThrow(AnalysisException.retryable(AnalysisErrorCode.NETWORK_ERROR, "연결 실패", null))
				.thenThrow(AnalysisException.retryable(AnalysisErrorCode.RATE_LIMIT, "Gemini HTTP 429", null));

		service.analyze(PROGRAM_ID);

		verify(geminiClient, times(3)).generate(anyString(), anyString(), any());
		verify(analysisMapper).updateFailed(ANALYSIS_ID, "RATE_LIMIT", "Gemini HTTP 429", 3);
	}

	@Test
	void 예상하지_못한_예외는_INTERNAL_ERROR로_기록해_ANALYZING에_남지_않는다() {
		when(programMapper.selectById(PROGRAM_ID)).thenReturn(program("{깨진 raw_content"));

		service.analyze(PROGRAM_ID);

		verify(geminiClient, never()).generate(anyString(), anyString(), any());
		verify(analysisMapper).updateFailed(eq(ANALYSIS_ID), eq("INTERNAL_ERROR"), anyString(), eq(0));
	}

	@Test
	void 다른_요청이_분석_중이면_예외() {
		when(analysisMapper.upsertAnalyzing(PROGRAM_ID, "gemini-3.8-flash", AnalysisPrompt.VERSION)).thenReturn(null);
		when(analysisMapper.selectByProgramAndVersion(PROGRAM_ID, AnalysisPrompt.VERSION))
				.thenReturn(null, row("ANALYZING"));

		assertThatThrownBy(() -> service.analyze(PROGRAM_ID)).isInstanceOf(AnalysisInProgressException.class);
		verify(geminiClient, never()).generate(anyString(), anyString(), any());
	}

	@Test
	void 조회와_upsert_사이에_다른_요청이_완료했으면_그_결과를_재사용한다() {
		when(analysisMapper.upsertAnalyzing(PROGRAM_ID, "gemini-3.8-flash", AnalysisPrompt.VERSION)).thenReturn(null);
		when(analysisMapper.selectByProgramAndVersion(PROGRAM_ID, AnalysisPrompt.VERSION))
				.thenReturn(null, row("COMPLETED"));

		assertThat(service.analyze(PROGRAM_ID).reused()).isTrue();
		verify(geminiClient, never()).generate(anyString(), anyString(), any());
	}

	@Test
	void 없는_공고는_예외() {
		assertThatThrownBy(() -> service.analyze(999L)).isInstanceOf(SupportProgramNotFoundException.class);
	}

	private void givenGeminiReturns(String text) {
		when(geminiClient.generate(anyString(), anyString(), any()))
				.thenReturn(new GeminiClient.GeminiResponse(text, "gemini-3.8-flash"));
	}

	private SupportProgram program(String rawContent) {
		return new SupportProgram(PROGRAM_ID, "PBLN_X", "t", null, null, null, null, null, null, null,
				rawContent, null, null);
	}

	private AiAnalysis row(String status) {
		return new AiAnalysis(ANALYSIS_ID, PROGRAM_ID, "gemini-3.8-flash", AnalysisPrompt.VERSION, status,
				null, null, null, null, null, null, null, null, null, null, 1, null, null, null);
	}

	private String read(String path) throws IOException {
		try (InputStream in = getClass().getResourceAsStream(path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
