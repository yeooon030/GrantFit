package com.grantfit.analysis;

import java.time.Duration;

/**
 * AI 분석 실패. 원인 코드와 함께 ai_analysis 에 FAILED 로 기록된다.
 * retryable 이면 같은 요청을 다시 보내 성공할 수 있는 일시적 오류다. (MILESTONE 3)
 */
public class AnalysisException extends RuntimeException {

	private final AnalysisErrorCode errorCode;
	private final boolean retryable;
	private final Duration retryAfter;

	public AnalysisException(AnalysisErrorCode errorCode, String message) {
		this(errorCode, message, false, null);
	}

	private AnalysisException(AnalysisErrorCode errorCode, String message, boolean retryable, Duration retryAfter) {
		super(message);
		this.errorCode = errorCode;
		this.retryable = retryable;
		this.retryAfter = retryAfter;
	}

	/**
	 * @param retryAfter 서버가 알려준 대기 시간. 없으면 null
	 */
	public static AnalysisException retryable(AnalysisErrorCode errorCode, String message, Duration retryAfter) {
		return new AnalysisException(errorCode, message, true, retryAfter);
	}

	public AnalysisErrorCode getErrorCode() {
		return errorCode;
	}

	public boolean isRetryable() {
		return retryable;
	}

	public Duration getRetryAfter() {
		return retryAfter;
	}
}
