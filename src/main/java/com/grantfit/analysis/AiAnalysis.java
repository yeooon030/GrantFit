package com.grantfit.analysis;

import java.time.OffsetDateTime;

/**
 * ai_analysis 테이블 1행. JSONB 컬럼은 JSON 문자열로 다룬다.
 */
public record AiAnalysis(
		Long id,
		Long supportProgramId,
		String model,
		String promptVersion,
		String status,
		String eligibilityJson,
		String exclusionJson,
		String requiredDocumentsJson,
		String supportAmountJson,
		String uncertainConditionsJson,
		String evidenceJson,
		String preferenceJson,
		String summary,
		String errorCode,
		String errorMessage,
		int attemptCount,
		OffsetDateTime analyzedAt,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {
}
