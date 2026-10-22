package com.grantfit.analysis;

/**
 * ai_analysis.error_code 값. DDL CHECK 제약과 일치해야 한다. (README §12)
 */
public enum AnalysisErrorCode {
	API_ERROR,
	TIMEOUT,
	RATE_LIMIT,
	INVALID_JSON,
	VALIDATION_ERROR,
	/** 연결 실패 등 응답을 받지 못한 경우 (MILESTONE 3) */
	NETWORK_ERROR,
	/** Gemini 가 아닌 서버 내부 문제 (raw_content 손상 등, MILESTONE 3) */
	INTERNAL_ERROR
}
