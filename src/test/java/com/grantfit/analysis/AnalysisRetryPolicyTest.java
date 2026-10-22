package com.grantfit.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class AnalysisRetryPolicyTest {

	private final AnalysisRetryPolicy policy =
			new AnalysisRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(30));

	@Test
	void 대기_시간은_1초부터_2배씩_늘어난다() {
		AnalysisException timeout = AnalysisException.retryable(AnalysisErrorCode.TIMEOUT, "t", null);

		assertThat(policy.delayBeforeRetry(1, timeout)).isEqualTo(Duration.ofSeconds(1));
		assertThat(policy.delayBeforeRetry(2, timeout)).isEqualTo(Duration.ofSeconds(2));
	}

	@Test
	void 최대_호출_횟수에_도달하면_재시도하지_않는다() {
		AnalysisException timeout = AnalysisException.retryable(AnalysisErrorCode.TIMEOUT, "t", null);

		assertThat(policy.delayBeforeRetry(3, timeout)).isNull();
	}

	@Test
	void 재시도_대상이_아니면_null() {
		assertThat(policy.delayBeforeRetry(1, new AnalysisException(AnalysisErrorCode.VALIDATION_ERROR, "v"))).isNull();
		assertThat(policy.delayBeforeRetry(1, new AnalysisException(AnalysisErrorCode.INVALID_JSON, "j"))).isNull();
	}

	@Test
	void 서버가_알려준_대기_시간이_더_길면_그만큼_기다린다() {
		AnalysisException rateLimit = AnalysisException.retryable(
				AnalysisErrorCode.RATE_LIMIT, "429", Duration.ofSeconds(17));

		assertThat(policy.delayBeforeRetry(1, rateLimit)).isEqualTo(Duration.ofSeconds(17));
	}

	@Test
	void 대기_시간이_상한을_넘으면_재시도하지_않는다() {
		AnalysisException rateLimit = AnalysisException.retryable(
				AnalysisErrorCode.RATE_LIMIT, "429", Duration.ofSeconds(60));

		assertThat(policy.delayBeforeRetry(1, rateLimit)).isNull();
	}
}
