package com.grantfit.bizinfo;

/**
 * 기업마당 API 호출 실패.
 * API 는 오류도 HTTP 200 + {"reqErr": "..."} 로 응답하므로 본문으로 판별한다.
 */
public class BizinfoApiException extends RuntimeException {

	public BizinfoApiException(String message) {
		super(message);
	}

	public BizinfoApiException(String message, Throwable cause) {
		super(message, cause);
	}
}
