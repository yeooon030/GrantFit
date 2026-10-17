package com.grantfit.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 실제 Gemini API 를 1회 호출해 현재 스키마가 받아들여지고 DTO 로 변환되는지 확인한다.
 * GEMINI_API_KEY 가 없으면 건너뛴다.
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
		assumeTrue(!apiKey.isBlank(), "GEMINI_API_KEY 미설정");
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
}
