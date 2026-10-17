package com.grantfit.analysis;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Gemini generateContent 클라이언트. (README §9 Structured Output)
 *
 * POST {base-url}/models/{model}:generateContent
 *   header x-goog-api-key
 *   generationConfig.responseMimeType = application/json
 *   generationConfig.responseJsonSchema = 응답 스키마
 *
 * 응답 JSON 텍스트: candidates[0].content.parts[].text
 */
@Component
public class GeminiClient {

	private static final int ERROR_BODY_LIMIT = 300;

	private final RestClient restClient;
	private final String model;
	private final String apiKey;

	public GeminiClient(
			RestClient.Builder restClientBuilder,
			@Value("${gemini.base-url}") String baseUrl,
			@Value("${gemini.model}") String model,
			@Value("${gemini.api-key:}") String apiKey,
			@Value("${gemini.timeout-seconds:60}") int timeoutSeconds) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(10));
		requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
		this.restClient = restClientBuilder.baseUrl(baseUrl).requestFactory(requestFactory).build();
		this.model = model;
		this.apiKey = apiKey;
	}

	public String model() {
		return model;
	}

	public GeminiResponse generate(String systemInstruction, String userText, JsonNode responseSchema) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new AnalysisException(AnalysisErrorCode.API_ERROR, "GEMINI_API_KEY 가 설정되지 않았습니다.");
		}

		Map<String, Object> request = Map.of(
				"systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
				"contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userText)))),
				"generationConfig", Map.of(
						"responseMimeType", "application/json",
						"responseJsonSchema", responseSchema,
						"temperature", 0));

		JsonNode body;
		try {
			body = restClient.post()
					.uri("/models/{model}:generateContent", model)
					.header("x-goog-api-key", apiKey)
					.body(request)
					.retrieve()
					.body(JsonNode.class);
		} catch (RestClientResponseException e) {
			AnalysisErrorCode code = e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()
					? AnalysisErrorCode.RATE_LIMIT
					: AnalysisErrorCode.API_ERROR;
			throw new AnalysisException(code,
					"Gemini HTTP " + e.getStatusCode().value() + ": " + truncate(e.getResponseBodyAsString()));
		} catch (ResourceAccessException e) {
			if (e.getCause() instanceof SocketTimeoutException) {
				throw new AnalysisException(AnalysisErrorCode.TIMEOUT, "Gemini 응답 시간 초과");
			}
			throw new AnalysisException(AnalysisErrorCode.API_ERROR, "Gemini 연결 실패: " + e.getClass().getSimpleName());
		} catch (RestClientException e) {
			throw new AnalysisException(AnalysisErrorCode.API_ERROR, "Gemini 호출 실패: " + e.getClass().getSimpleName());
		}

		return parse(body);
	}

	GeminiResponse parse(JsonNode body) {
		JsonNode candidate = body == null ? null : body.path("candidates").path(0);
		if (candidate == null || candidate.isMissingNode()) {
			// 입력이 차단되면 candidates 없이 promptFeedback 만 온다.
			String feedback = body == null ? "" : body.path("promptFeedback").toString();
			throw new AnalysisException(AnalysisErrorCode.API_ERROR, "Gemini 응답에 candidates 가 없음 " + truncate(feedback));
		}

		String finishReason = candidate.path("finishReason").asText("");
		if ("MAX_TOKENS".equals(finishReason)) {
			throw new AnalysisException(AnalysisErrorCode.INVALID_JSON, "응답이 최대 토큰에서 잘림 (finishReason=MAX_TOKENS)");
		}
		if (!"STOP".equals(finishReason)) {
			throw new AnalysisException(AnalysisErrorCode.API_ERROR, "비정상 종료 finishReason=" + finishReason);
		}

		StringBuilder text = new StringBuilder();
		for (JsonNode part : candidate.path("content").path("parts")) {
			// thinking 모델의 사고 요약 part 는 제외한다.
			if (!part.path("thought").asBoolean(false) && part.has("text")) {
				text.append(part.get("text").asText());
			}
		}
		if (text.isEmpty()) {
			throw new AnalysisException(AnalysisErrorCode.INVALID_JSON, "응답 텍스트가 비어 있음");
		}

		String modelVersion = body.path("modelVersion").asText(model);
		return new GeminiResponse(text.toString(), modelVersion);
	}

	private static String truncate(String text) {
		if (text == null) {
			return "";
		}
		return text.length() <= ERROR_BODY_LIMIT ? text : text.substring(0, ERROR_BODY_LIMIT) + "…";
	}

	public record GeminiResponse(String text, String modelVersion) {
	}
}
