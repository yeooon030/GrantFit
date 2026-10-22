package com.grantfit.analysis;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 공고 1건 AI 분석 실행. (README §28 MILESTONE 2)
 * 분석 실패도 결과(FAILED + error_code)이므로 200 으로 응답한다.
 */
@RestController
@RequestMapping("/api/support-programs")
public class AnalysisController {

	private final AnalysisService analysisService;
	private final ObjectMapper objectMapper;

	public AnalysisController(AnalysisService analysisService, ObjectMapper objectMapper) {
		this.analysisService = analysisService;
		this.objectMapper = objectMapper;
	}

	@PostMapping("/{id}/analyze")
	public AnalysisResponse analyze(@PathVariable long id) {
		AnalysisService.AnalysisOutcome outcome = analysisService.analyze(id);
		return toResponse(outcome.analysis(), outcome.reused());
	}

	@ExceptionHandler(AnalysisInProgressException.class)
	public ResponseEntity<Map<String, Object>> handleInProgress(AnalysisInProgressException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
	}

	@ExceptionHandler(SupportProgramNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNotFound(SupportProgramNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
	}

	private AnalysisResponse toResponse(AiAnalysis a, boolean reused) {
		Map<String, JsonNode> result = null;
		if ("COMPLETED".equals(a.status())) {
			result = new LinkedHashMap<>();
			result.put("summary", objectMapper.getNodeFactory().textNode(a.summary()));
			result.put("eligibility", read(a.eligibilityJson()));
			result.put("exclusions", read(a.exclusionJson()));
			result.put("preferences", read(a.preferenceJson()));
			result.put("support", read(a.supportAmountJson()));
			result.put("requiredDocuments", read(a.requiredDocumentsJson()));
			result.put("uncertainConditions", read(a.uncertainConditionsJson()));
			result.put("evidence", read(a.evidenceJson()));
		}
		return new AnalysisResponse(
				a.id(), a.supportProgramId(), a.status(), a.model(), a.promptVersion(), reused,
				a.errorCode(), a.errorMessage(), a.attemptCount(), a.analyzedAt(), result);
	}

	private JsonNode read(String json) {
		if (json == null) {
			return null;
		}
		try {
			return objectMapper.readTree(json);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("저장된 분석 결과를 읽을 수 없습니다.", e);
		}
	}

	public record AnalysisResponse(
			Long analysisId,
			Long supportProgramId,
			String status,
			String model,
			String promptVersion,
			boolean reused,
			String errorCode,
			String errorMessage,
			int attemptCount,
			OffsetDateTime analyzedAt,
			Map<String, JsonNode> result) {
	}
}
