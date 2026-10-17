package com.grantfit.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Gemini 응답 → 텍스트 추출만 검증한다. 네트워크를 사용하지 않는다.
 * 성공 샘플은 2026-10-17 gemini-3.8-flash 실제 응답이다.
 */
class GeminiClientParseTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final GeminiClient client =
			new GeminiClient(RestClient.builder(), "http://localhost", "test-model", "test-key", 5);

	@Test
	void 실제_응답에서_JSON_텍스트와_모델_버전을_꺼낸다() throws IOException {
		GeminiClient.GeminiResponse response = client.parse(readSample());

		assertThat(response.modelVersion()).isEqualTo("gemini-3.8-flash");
		AnalysisResult result = objectMapper.readValue(response.text(), AnalysisResult.class);
		assertThat(result.regions()).containsExactly("전남광주");
		assertThat(result.eligibility()).isNotEmpty();
	}

	@Test
	void thought_part는_제외한다() throws IOException {
		JsonNode body = objectMapper.readTree("""
				{"candidates":[{"finishReason":"STOP","content":{"parts":[
				  {"text":"생각 중...","thought":true},
				  {"text":"{\\"a\\":1}"}]}}]}
				""");

		assertThat(client.parse(body).text()).isEqualTo("{\"a\":1}");
	}

	@Test
	void MAX_TOKENS는_INVALID_JSON() throws IOException {
		JsonNode body = objectMapper.readTree("""
				{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"{\\"a\\":"}]}}]}
				""");

		assertThatThrownBy(() -> client.parse(body))
				.isInstanceOf(AnalysisException.class)
				.extracting(e -> ((AnalysisException) e).getErrorCode())
				.isEqualTo(AnalysisErrorCode.INVALID_JSON);
	}

	@Test
	void SAFETY_등_비정상_종료는_API_ERROR() throws IOException {
		JsonNode body = objectMapper.readTree("""
				{"candidates":[{"finishReason":"SAFETY","content":{"parts":[]}}]}
				""");

		assertThatThrownBy(() -> client.parse(body))
				.extracting(e -> ((AnalysisException) e).getErrorCode())
				.isEqualTo(AnalysisErrorCode.API_ERROR);
	}

	@Test
	void candidates가_없으면_API_ERROR() throws IOException {
		JsonNode body = objectMapper.readTree("{\"promptFeedback\":{\"blockReason\":\"OTHER\"}}");

		assertThatThrownBy(() -> client.parse(body))
				.hasMessageContaining("candidates")
				.extracting(e -> ((AnalysisException) e).getErrorCode())
				.isEqualTo(AnalysisErrorCode.API_ERROR);
	}

	private JsonNode readSample() throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/analysis/gemini-response-success.json")) {
			return objectMapper.readTree(in);
		}
	}
}
