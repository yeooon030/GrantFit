package com.grantfit.program;

import java.util.List;

public record SupportProgramPage(
		List<SupportProgramSummary> items,
		int page,
		int size,
		long totalCount) {
}
