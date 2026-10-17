package com.grantfit.analysis;

/**
 * AI 분석 실패. 원인 코드와 함께 ai_analysis 에 FAILED 로 기록된다.
 */
public class AnalysisException extends RuntimeException {

	private final AnalysisErrorCode errorCode;

	public AnalysisException(AnalysisErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public AnalysisErrorCode getErrorCode() {
		return errorCode;
	}
}
