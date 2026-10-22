package com.grantfit.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 실제 Gemini API 를 호출한다. GEMINI_LIVE_TEST=true 일 때만 실행한다.
 *
 * 무료 등급 gemini-3.8-flash 는 하루 20회 한도라서, ./gradlew test 마다 호출하면 한도를 소모한다.
 * (2026-10-22 확인: quotaId=GenerateRequestsPerDayPerProjectPerModel-FreeTier, quotaValue=20)
 *   GEMINI_LIVE_TEST=true ./gradlew test --tests '*GeminiClientLiveTest'
 * - 현재 스키마가 받아들여지고 DTO 로 변환되는지
 * - 실제 응답이 타임아웃보다 늦을 때 TIMEOUT 으로 분류되는지 (Mock 으로는 재현되지 않았던 경우)
 */
@SpringBootTest
class GeminiClientLiveTest {

	@Autowired
	private GeminiClient geminiClient;

	@Autowired
	private AnalysisPrompt analysisPrompt;

	@Autowired
	private ObjectMapper objectMapper;

	@Value("${gemini.api-key:}")
	private String apiKey;

	@Test
	void 실제_Gemini가_스키마대로_응답한다() throws Exception {
		assumeLiveTest();
		String input = """
				[공고명]
				2026년 서울 AI 스타트업 사업화 지원
				[사업개요]
				서울특별시에 사업장을 둔 창업 7년 이내 기업을 대상으로 기업당 최대 5천만원을 지원한다.
				""";

		GeminiClient.GeminiResponse response = geminiClient.generate(
				analysisPrompt.systemInstruction(), input, analysisPrompt.responseSchema());

		AnalysisResult result = objectMapper.readValue(response.text(), AnalysisResult.class);
		assertThat(response.modelVersion()).isNotBlank();
		assertThat(result.summary()).isNotBlank();
		assertThat(result.regions()).isNotNull();
		assertThat(result.evidence()).isNotNull();
	}

	@Test
	void 실제_응답이_타임아웃보다_늦으면_TIMEOUT으로_분류한다() {
		assumeLiveTest();
		GeminiClient shortTimeout = new GeminiClient(RestClient.builder(),
				"https://generativelanguage.googleapis.com/v1beta", geminiClient.model(), apiKey, 1);

		AnalysisException e = catchThrowableOfType(AnalysisException.class, () -> shortTimeout.generate(
				analysisPrompt.systemInstruction(), "[공고명]\n테스트 공고", analysisPrompt.responseSchema()));

		assertThat(e).isNotNull();
		assertThat(e.getErrorCode()).isEqualTo(AnalysisErrorCode.TIMEOUT);
		assertThat(e.isRetryable()).isTrue();
	}

	private void assumeLiveTest() {
		assumeTrue("true".equals(System.getenv("GEMINI_LIVE_TEST")), "GEMINI_LIVE_TEST=true 가 아니면 건너뜀 (일일 한도 보호)");
		assumeTrue(!apiKey.isBlank(), "GEMINI_API_KEY 미설정");
	}
}
