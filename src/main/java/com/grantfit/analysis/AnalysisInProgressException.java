package com.grantfit.analysis;

/**
 * 같은 공고·prompt_version 을 다른 요청이 분석 중이다.
 */
public class AnalysisInProgressException extends RuntimeException {

	public AnalysisInProgressException(long supportProgramId) {
		super("이미 분석 중인 공고입니다. id=" + supportProgramId);
	}
}
