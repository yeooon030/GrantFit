package com.grantfit.evaluation;

import java.util.List;
import java.util.Map;

/**
 * AI 정확도 측정 결과. (README §14, MILESTONE 4)
 */
public record EvaluationReport(
		String promptVersion,
		int goldenItems,
		int labeledItems,
		int evaluatedItems,
		List<String> notAnalyzed,
		Map<String, Score> fields,
		Score mainFieldAccuracy,
		Score applicationPeriod,
		Score evidenceCoverage,
		StructuredOutput structuredOutput,
		List<Mismatch> mismatches) {

	public record Score(int correct, int total, Double rate) {

		static Score of(int correct, int total) {
			Double rate = total == 0 ? null : Math.round(correct * 1000.0 / total) / 1000.0;
			return new Score(correct, total, rate);
		}
	}

	/**
	 * 성공률 = completed / (completed + invalidJson + validationError).
	 * Gemini 과부하·한도·네트워크 등 인프라 실패는 AI 출력 품질과 무관하므로 분모에서 뺀다.
	 */
	public record StructuredOutput(int completed, int invalidJson, int validationError, int infraFailures, Double rate) {
	}

	public record Mismatch(String externalId, String title, String field, Object expected, Object actual) {
	}
}
