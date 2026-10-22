package com.grantfit.analysis;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 일괄 분석 실행용 관리 API. 로컬 개발용이며 인증이 없다. (README §12)
 */
@RestController
@RequestMapping("/api/admin/analysis")
public class AnalysisBatchController {

	private static final int MAX_LIMIT = 50;

	private final AnalysisBatchService analysisBatchService;

	public AnalysisBatchController(AnalysisBatchService analysisBatchService) {
		this.analysisBatchService = analysisBatchService;
	}

	@PostMapping("/batch")
	public ResponseEntity<?> batch(@RequestParam(defaultValue = "20") int limit) {
		if (limit < 1 || limit > MAX_LIMIT) {
			return ResponseEntity.badRequest().body(Map.of("error", "limit 는 1~" + MAX_LIMIT + " 이어야 합니다."));
		}
		return ResponseEntity.ok(analysisBatchService.analyzePending(limit));
	}
}
