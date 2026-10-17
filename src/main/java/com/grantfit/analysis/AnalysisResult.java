package com.grantfit.analysis;

import java.util.List;

/**
 * Gemini Structured Output 응답 DTO. (README §9)
 * 스키마: resources/ai/analysis-schema-v1.json
 */
public record AnalysisResult(
		String summary,
		List<String> regions,
		BusinessAge businessAge,
		List<String> applicantTypes,
		Support support,
		List<Condition> eligibility,
		List<Condition> exclusions,
		List<Condition> preferences,
		List<String> requiredDocuments,
		List<String> uncertainConditions,
		Evidence evidence) {

	public record BusinessAge(Integer minYears, Integer maxYears) {
	}

	public record Support(Long maxAmount, Boolean selfPaymentRequired) {
	}

	public record Condition(String condition, String evidence) {
	}

	/** regions, businessAge, applicantTypes, support 값의 근거 문장 */
	public record Evidence(String regions, String businessAge, String applicantTypes, String support) {
	}
}
