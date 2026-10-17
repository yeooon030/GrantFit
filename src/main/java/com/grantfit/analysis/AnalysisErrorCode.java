package com.grantfit.analysis;

/**
 * ai_analysis.error_code 값. DDL CHECK 제약과 일치해야 한다. (README §12)
 */
public enum AnalysisErrorCode {
	API_ERROR,
	TIMEOUT,
	RATE_LIMIT,
	INVALID_JSON,
	VALIDATION_ERROR
}
