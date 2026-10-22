package com.grantfit.analysis;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gemini 호출 재시도 정책. (README MILESTONE 3 "제한된 횟수만 재시도")
 *
 * - 재시도 대상: AnalysisException.isRetryable() (TIMEOUT, RATE_LIMIT, 5xx, NETWORK_ERROR)
 * - 대기: initialBackoff, 2배씩 증가. 서버가 retryAfter 를 주면 둘 중 긴 쪽
 * - 대기 시간이 maxWait 를 넘으면 재시도하지 않는다
 */
@Component
public class AnalysisRetryPolicy {

	private final int maxAttempts;
	private final Duration initialBackoff;
	private final Duration maxWait;

	@Autowired
	public AnalysisRetryPolicy(
			@Value("${gemini.retry.max-attempts:3}") int maxAttempts,
			@Value("${gemini.retry.initial-backoff-millis:1000}") long initialBackoffMillis,
			@Value("${gemini.retry.max-wait-seconds:30}") long maxWaitSeconds) {
		this(maxAttempts, Duration.ofMillis(initialBackoffMillis), Duration.ofSeconds(maxWaitSeconds));
	}

	AnalysisRetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxWait) {
		this.maxAttempts = maxAttempts;
		this.initialBackoff = initialBackoff;
		this.maxWait = maxWait;
	}

	/**
	 * @param attempts 지금까지 호출한 횟수
	 * @return 다음 호출 전 대기 시간. 재시도하지 않아야 하면 null
	 */
	public Duration delayBeforeRetry(int attempts, AnalysisException failure) {
		if (!failure.isRetryable() || attempts >= maxAttempts) {
			return null;
		}
		Duration backoff = initialBackoff.multipliedBy(1L << (attempts - 1));
		Duration retryAfter = failure.getRetryAfter();
		Duration delay = retryAfter != null && retryAfter.compareTo(backoff) > 0 ? retryAfter : backoff;
		return delay.compareTo(maxWait) > 0 ? null : delay;
	}

	public void sleep(Duration delay) {
		try {
			Thread.sleep(delay.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AnalysisException(AnalysisErrorCode.INTERNAL_ERROR, "재시도 대기 중 인터럽트");
		}
	}
}
