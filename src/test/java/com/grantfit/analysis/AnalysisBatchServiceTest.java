package com.grantfit.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AnalysisBatchServiceTest {

	private final AnalysisService analysisService = mock(AnalysisService.class);
	private final AiAnalysisMapper mapper = mock(AiAnalysisMapper.class);
	private final AnalysisBatchService batch = new AnalysisBatchService(analysisService, mapper,
			new AnalysisRetryPolicy(3, Duration.ZERO, Duration.ZERO), 0);

	@Test
	void 결과를_README_12_형식으로_집계한다() {
		when(mapper.selectProgramIdsToAnalyze(AnalysisPrompt.VERSION, 20)).thenReturn(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L));
		when(analysisService.analyze(1L)).thenReturn(outcome(1L, "COMPLETED", null, 1));
		when(analysisService.analyze(2L)).thenReturn(outcome(2L, "COMPLETED", null, 3));
		when(analysisService.analyze(3L)).thenReturn(outcome(3L, "FAILED", "VALIDATION_ERROR", 1));
		when(analysisService.analyze(4L)).thenReturn(outcome(4L, "FAILED", "TIMEOUT", 3));
		when(analysisService.analyze(5L)).thenThrow(new AnalysisInProgressException(5L));
		when(analysisService.analyze(6L)).thenReturn(outcome(6L, "FAILED", "RATE_LIMIT", 1));

		AnalysisBatchService.BatchResult result = batch.analyzePending(20);

		// 6번에서 RATE_LIMIT → 7, 8번은 호출하지 않는다
		assertThat(result.total()).isEqualTo(8);
		assertThat(result.completed()).isEqualTo(2);
		assertThat(result.completedAfterRetry()).isEqualTo(1);
		assertThat(result.failed()).isEqualTo(3);
		assertThat(result.failuresByCode()).isEqualTo(Map.of("VALIDATION_ERROR", 1, "TIMEOUT", 1, "RATE_LIMIT", 1));
		assertThat(result.skipped()).isEqualTo(1);
		assertThat(result.stoppedByRateLimit()).isTrue();
		assertThat(result.notAttempted()).isEqualTo(2);
		assertThat(result.items()).hasSize(6);
		verify(analysisService, never()).analyze(7L);
	}

	private AnalysisService.AnalysisOutcome outcome(long programId, String status, String code, int attempts) {
		return new AnalysisService.AnalysisOutcome(new AiAnalysis(programId * 10, programId, "m", "v1", status,
				null, null, null, null, null, null, null, null, code, null, attempts, null, null, null), false);
	}
}
