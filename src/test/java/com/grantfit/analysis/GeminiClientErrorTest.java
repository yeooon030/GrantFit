package com.grantfit.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Gemini HTTP 오류 → error_code / 재시도 여부 분류. (MILESTONE 3)
 * 실제 오류 응답을 재현하기 어려운 경우(429, 5xx, 연결 실패)를 MockRestServiceServer 로 검증한다.
 */
class GeminiClientErrorTest {

	private static final String URL = "http://gemini.test/models/test-model:generateContent";

	private MockRestServiceServer server;
	private GeminiClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new GeminiClient(builder.build(), "test-model", "test-key");
	}

	@Test
	void 요청은_API_키를_헤더로_보낸다() {
		server.expect(requestTo(URL)).andExpect(header("x-goog-api-key", "test-key"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST));

		call();
		server.verify();
	}

	@Test
	void HTTP_429는_RATE_LIMIT_재시도_대상이고_retryDelay를_읽는다() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","details":[
						  {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"17s"}]}}
						"""));

		AnalysisException e = call();

		assertThat(e.getErrorCode()).isEqualTo(AnalysisErrorCode.RATE_LIMIT);
		assertThat(e.isRetryable()).isTrue();
		assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(17));
	}

	@Test
	void 서버_오류_5xx는_API_ERROR_재시도_대상() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		AnalysisException e = call();

		assertThat(e.getErrorCode()).isEqualTo(AnalysisErrorCode.API_ERROR);
		assertThat(e.isRetryable()).isTrue();
		assertThat(e.getMessage()).startsWith("Gemini HTTP 503");
	}

	@Test
	void 키_오류_등_4xx는_재시도하지_않는다() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

		AnalysisException e = call();

		assertThat(e.getErrorCode()).isEqualTo(AnalysisErrorCode.API_ERROR);
		assertThat(e.isRetryable()).isFalse();
	}

	@Test
	void 응답_시간_초과는_TIMEOUT_재시도_대상() {
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

		AnalysisException e = call();

		assertThat(e.getErrorCode()).isEqualTo(AnalysisErrorCode.TIMEOUT);
		assertThat(e.isRetryable()).isTrue();
	}

	@Test
	void 연결_실패는_NETWORK_ERROR_재시도_대상() {
		server.expect(requestTo(URL)).andRespond(withException(new ConnectException("Connection refused")));

		AnalysisException e = call();

		assertThat(e.getErrorCode()).isEqualTo(AnalysisErrorCode.NETWORK_ERROR);
		assertThat(e.isRetryable()).isTrue();
		assertThat(e.getMessage()).contains("ConnectException");
	}

	@Test
	void 본문_읽는_중_시간_초과도_TIMEOUT() {
		// 실제 Gemini 에서 관찰된 형태: RestClientException("Error while extracting response") ← SocketTimeoutException
		AnalysisException e = GeminiClient.classifyIoFailure(new RestClientException(
				"Error while extracting response", new SocketTimeoutException("Read timed out")));

		assertThat(e.getErrorCode()).isEqualTo(AnalysisErrorCode.TIMEOUT);
		assertThat(e.isRetryable()).isTrue();
	}

	@Test
	void 원인을_알_수_없는_클라이언트_오류는_재시도하지_않는_API_ERROR() {
		AnalysisException e = GeminiClient.classifyIoFailure(new RestClientException("unknown"));

		assertThat(e.getErrorCode()).isEqualTo(AnalysisErrorCode.API_ERROR);
		assertThat(e.isRetryable()).isFalse();
	}

	@Test
	void 오류_요약은_한도_종류를_남긴다() {
		// 2026-10-22 실제 응답 (무료 등급 일일 한도)
		String body = """
				{"error":{"code":429,"message":"You exceeded your current quota.\\n* Quota exceeded for metric: generate_content_free_tier_requests, limit: 20","status":"RESOURCE_EXHAUSTED",
				 "details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[
				   {"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier","quotaValue":"20"}]},
				   {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"1238s"}]}}
				""";

		assertThat(GeminiClient.errorSummary(body))
				.startsWith("RESOURCE_EXHAUSTED You exceeded your current quota. * Quota exceeded")
				.endsWith("[quotaId=GenerateRequestsPerDayPerProjectPerModel-FreeTier, quotaValue=20]");
		assertThat(GeminiClient.retryDelay(body)).isEqualTo(Duration.ofSeconds(1238));
	}

	@Test
	void retryDelay_형식() {
		assertThat(GeminiClient.retryDelay("{\"error\":{\"details\":[{\"retryDelay\":\"1.5s\"}]}}"))
				.isEqualTo(Duration.ofMillis(1500));
		assertThat(GeminiClient.retryDelay("not json")).isNull();
		assertThat(GeminiClient.retryDelay("{\"error\":{}}")).isNull();
	}

	private AnalysisException call() {
		return catchThrowableOfType(AnalysisException.class,
				() -> client.generate("system", "text", new ObjectMapper().createObjectNode()));
	}
}
