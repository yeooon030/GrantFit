package com.grantfit.analysis;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Gemini generateContent 클라이언트. (README §9 Structured Output)
 *
 * POST {base-url}/models/{model}:generateContent
 *   header x-goog-api-key
 *   generationConfig.responseMimeType = application/json
 *   generationConfig.responseJsonSchema = 응답 스키마
 *
 * 응답 JSON 텍스트: candidates[0].content.parts[].text
 *
 * 오류 분류 (MILESTONE 3):
 *   429                → RATE_LIMIT    재시도 (error.details[].retryDelay 참고)
 *   5xx                → API_ERROR     재시도
 *   그 외 4xx(400/401/403/404) → API_ERROR 재시도 안 함 (요청·키 문제)
 *   응답 시간 초과      → TIMEOUT       재시도
 *   연결 실패           → NETWORK_ERROR 재시도
 */
@Component
public class GeminiClient {

	private static final int ERROR_BODY_LIMIT = 500;
	private static final Pattern DELAY_SECONDS = Pattern.compile("^(\\d+(?:\\.\\d+)?)s$");
	private static final ObjectMapper ERROR_READER = new ObjectMapper();

	private final RestClient restClient;
	private final String model;
	private final String apiKey;

	@Autowired
	public GeminiClient(
			RestClient.Builder restClientBuilder,
			@Value("${gemini.base-url}") String baseUrl,
			@Value("${gemini.model}") String model,
			@Value("${gemini.api-key:}") String apiKey,
			@Value("${gemini.timeout-seconds:60}") int timeoutSeconds) {
		this(restClientBuilder.baseUrl(baseUrl).requestFactory(requestFactory(timeoutSeconds)).build(), model, apiKey);
	}

	// 테스트에서 MockRestServiceServer 에 묶인 RestClient 를 넣기 위한 생성자
	GeminiClient(RestClient restClient, String model, String apiKey) {
		this.restClient = restClient;
		this.model = model;
		this.apiKey = apiKey;
	}

	private static SimpleClientHttpRequestFactory requestFactory(int timeoutSeconds) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(10));
		requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
		return requestFactory;
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
			int status = e.getStatusCode().value();
			String message = "Gemini HTTP " + status + ": " + errorSummary(e.getResponseBodyAsString());
			if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
				throw AnalysisException.retryable(AnalysisErrorCode.RATE_LIMIT, message,
						retryDelay(e.getResponseBodyAsString()));
			}
			if (e.getStatusCode().is5xxServerError()) {
				throw AnalysisException.retryable(AnalysisErrorCode.API_ERROR, message, null);
			}
			throw new AnalysisException(AnalysisErrorCode.API_ERROR, message);
		} catch (RestClientException e) {
			throw classifyIoFailure(e);
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

	/**
	 * HTTP 응답 코드를 받지 못한 실패를 분류한다.
	 *
	 * Gemini 는 헤더를 먼저 보내고 본문을 나중에 보내므로, 읽기 시간 초과는 본문 변환 중에 난다.
	 * 이때 Spring 은 ResourceAccessException 이 아닌 RestClientException 으로 감싸므로
	 * 예외 타입이 아니라 원인 체인의 SocketTimeoutException 으로 판별한다. (2026-10-22 실제 호출로 확인)
	 */
	static AnalysisException classifyIoFailure(RestClientException e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof SocketTimeoutException) {
				return AnalysisException.retryable(AnalysisErrorCode.TIMEOUT, "Gemini 응답 시간 초과", null);
			}
		}
		if (e instanceof ResourceAccessException) {
			String cause = e.getCause() == null ? e.getClass().getSimpleName() : e.getCause().getClass().getSimpleName();
			return AnalysisException.retryable(AnalysisErrorCode.NETWORK_ERROR, "Gemini 연결 실패: " + cause, null);
		}
		return new AnalysisException(AnalysisErrorCode.API_ERROR, "Gemini 호출 실패: " + e.getClass().getSimpleName());
	}

	/**
	 * 오류 본문에서 원인을 판단할 수 있는 부분만 남긴다. (error.status, error.message, QuotaFailure.quotaId)
	 * 본문 앞부분을 자르면 429 의 한도 종류(분당/일일)가 잘려 나가 원인을 알 수 없었다.
	 */
	static String errorSummary(String errorBody) {
		try {
			JsonNode error = ERROR_READER.readTree(errorBody).path("error");
			if (error.isMissingNode()) {
				return truncate(errorBody);
			}
			StringBuilder summary = new StringBuilder(error.path("status").asText(""));
			summary.append(" ").append(error.path("message").asText("").replaceAll("\\s+", " ").strip());
			for (JsonNode detail : error.path("details")) {
				for (JsonNode violation : detail.path("violations")) {
					if (violation.has("quotaId")) {
						summary.append(" [quotaId=").append(violation.path("quotaId").asText())
								.append(", quotaValue=").append(violation.path("quotaValue").asText()).append("]");
					}
				}
			}
			return summary.toString().strip();
		} catch (JsonProcessingException | IllegalArgumentException e) {
			return truncate(errorBody);
		}
	}

	/**
	 * 429 응답 본문의 RetryInfo 에서 대기 시간을 읽는다.
	 * 예: {"error":{"details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"17s"}]}}
	 */
	static Duration retryDelay(String errorBody) {
		try {
			for (JsonNode detail : ERROR_READER.readTree(errorBody).path("error").path("details")) {
				Matcher matcher = DELAY_SECONDS.matcher(detail.path("retryDelay").asText(""));
				if (matcher.matches()) {
					return Duration.ofMillis(Math.round(Double.parseDouble(matcher.group(1)) * 1000));
				}
			}
		} catch (JsonProcessingException | IllegalArgumentException e) {
			// 본문이 JSON 이 아니면 대기 시간 정보가 없는 것으로 본다.
		}
		return null;
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
