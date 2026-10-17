package com.grantfit.analysis;

public class SupportProgramNotFoundException extends RuntimeException {

	public SupportProgramNotFoundException(long id) {
		super("지원사업을 찾을 수 없습니다. id=" + id);
	}
}
