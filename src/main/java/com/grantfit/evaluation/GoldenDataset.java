package com.grantfit.evaluation;

import java.time.LocalDate;
import java.util.List;

/**
 * 사람이 작성한 기준값. (README §13, evaluation/golden-dataset.json)
 * 작성 기준: evaluation/README.md
 */
public record GoldenDataset(String description, List<Item> items) {

	public record Item(
			String externalId,
			String title,
			boolean labeled,
			List<String> regions,
			BusinessAge businessAge,
			Long maxAmount,
			List<String> applicantTypes,
			LocalDate applicationStart,
			LocalDate applicationEnd,
			String note) {
	}

	public record BusinessAge(Integer minYears, Integer maxYears) {
	}
}
