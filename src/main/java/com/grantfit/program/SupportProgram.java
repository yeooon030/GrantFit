package com.grantfit.program;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * support_program 테이블 1행.
 * INSERT 시 id, createdAt, updatedAt 은 null 로 두고 DB 기본값을 사용한다.
 */
public record SupportProgram(
		Long id,
		String externalId,
		String title,
		String organization,
		String region,
		String target,
		LocalDate applicationStart,
		LocalDate applicationEnd,
		String supportAmount,
		String originalUrl,
		String rawContent,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {
}
