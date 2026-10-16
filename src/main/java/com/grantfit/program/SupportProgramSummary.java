package com.grantfit.program;

import java.time.LocalDate;

/**
 * 목록 화면용 공고 요약. raw_content 는 포함하지 않는다.
 */
public record SupportProgramSummary(
		Long id,
		String title,
		String organization,
		String region,
		String target,
		LocalDate applicationStart,
		LocalDate applicationEnd,
		String originalUrl) {
}
