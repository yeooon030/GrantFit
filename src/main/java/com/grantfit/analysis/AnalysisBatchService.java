package com.grantfit.analysis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 여러 공고 순차 분석. (README §12)
 *
 * 공고마다 AnalysisService 를 그대로 호출하므로 재시도·실패 기록 규칙이 1건 분석과 같다.
 * Gemini 부하를 줄이기 위해 공고 사이에 간격을 둔다.
 *
 * RATE_LIMIT 으로 최종 실패하면 나머지 공고는 분석하지 않고 멈춘다.
 * 재시도로도 풀리지 않은 한도(무료 등급 일일 20회 등)에 남은 요청을 보내도 모두 실패하기 때문이다.
 */
@Service
public class AnalysisBatchService {

	private static final Logger log = LoggerFactory.getLogger(AnalysisBatchService.class);

	private final AnalysisService analysisService;
	private final AiAnalysisMapper aiAnalysisMapper;
	private final AnalysisRetryPolicy retryPolicy;
	private final Duration interval;

	public AnalysisBatchService(
			AnalysisService analysisService,
			AiAnalysisMapper aiAnalysisMapper,
			AnalysisRetryPolicy retryPolicy,
			@Value("${gemini.batch.interval-millis:1000}") long intervalMillis) {
		this.analysisService = analysisService;
		this.aiAnalysisMapper = aiAnalysisMapper;
		this.retryPolicy = retryPolicy;
		this.interval = Duration.ofMillis(intervalMillis);
	}

	public BatchResult analyzePending(int limit) {
		List<Long> programIds = aiAnalysisMapper.selectProgramIdsToAnalyze(AnalysisPrompt.VERSION, limit);

		int completed = 0;
		int completedAfterRetry = 0;
		int failed = 0;
		int skipped = 0;
		boolean stoppedByRateLimit = false;
		Map<String, Integer> failuresByCode = new LinkedHashMap<>();
		List<Item> items = new ArrayList<>();

		for (int i = 0; i < programIds.size(); i++) {
			long programId = programIds.get(i);
			if (i > 0) {
				retryPolicy.sleep(interval);
			}
			try {
				AiAnalysis a = analysisService.analyze(programId).analysis();
				items.add(new Item(programId, a.id(), a.status(), a.errorCode(), a.attemptCount()));
				if ("COMPLETED".equals(a.status())) {
					completed++;
					if (a.attemptCount() > 1) {
						completedAfterRetry++;
					}
				} else {
					failed++;
					failuresByCode.merge(a.errorCode(), 1, Integer::sum);
					if (AnalysisErrorCode.RATE_LIMIT.name().equals(a.errorCode())) {
						stoppedByRateLimit = true;
						break;
					}
				}
			} catch (AnalysisInProgressException e) {
				skipped++;
				items.add(new Item(programId, null, "SKIPPED_IN_PROGRESS", null, 0));
			}
		}

		int notAttempted = programIds.size() - items.size();
		log.info("일괄 분석 total={} completed={} (재시도 후 {}) failed={} {} skipped={} notAttempted={} stoppedByRateLimit={}",
				programIds.size(), completed, completedAfterRetry, failed, failuresByCode, skipped,
				notAttempted, stoppedByRateLimit);
		return new BatchResult(programIds.size(), completed, completedAfterRetry, failed, failuresByCode, skipped,
				notAttempted, stoppedByRateLimit, items);
	}

	public record BatchResult(
			int total,
			int completed,
			int completedAfterRetry,
			int failed,
			Map<String, Integer> failuresByCode,
			int skipped,
			int notAttempted,
			boolean stoppedByRateLimit,
			List<Item> items) {
	}

	public record Item(long supportProgramId, Long analysisId, String status, String errorCode, int attemptCount) {
	}
}
