package com.grantfit.analysis;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantfit.program.SupportProgram;
import com.grantfit.program.SupportProgramMapper;

/**
 * 분석 관리 API. 로컬 개발용이며 인증이 없다. (README §12, MILESTONE 4)
 */
@RestController
@RequestMapping("/api/admin/analysis")
public class AnalysisBatchController {

	private static final int MAX_LIMIT = 50;

	private final AnalysisBatchService analysisBatchService;
	private final SupportProgramMapper supportProgramMapper;
	private final ObjectMapper objectMapper;

	public AnalysisBatchController(
			AnalysisBatchService analysisBatchService,
			SupportProgramMapper supportProgramMapper,
			ObjectMapper objectMapper) {
		this.analysisBatchService = analysisBatchService;
		this.supportProgramMapper = supportProgramMapper;
		this.objectMapper = objectMapper;
	}

	/**
	 * Gemini 에 보내는 입력 텍스트를 그대로 보여준다.
	 * Golden Dataset 작성(MILESTONE 4)과 분석 결과 디버깅에 쓴다. Gemini 를 호출하지 않는다.
	 */
	@GetMapping(value = "/input/{programId}", produces = "text/plain;charset=UTF-8")
	public ResponseEntity<String> input(@PathVariable long programId) {
		SupportProgram program = supportProgramMapper.selectById(programId);
		if (program == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(AnalysisInput.fromRawContent(program.rawContent(), objectMapper).text());
	}

	@PostMapping("/batch")
	public ResponseEntity<?> batch(@RequestParam(defaultValue = "20") int limit) {
		if (limit < 1 || limit > MAX_LIMIT) {
			return ResponseEntity.badRequest().body(Map.of("error", "limit 는 1~" + MAX_LIMIT + " 이어야 합니다."));
		}
		return ResponseEntity.ok(analysisBatchService.analyzePending(limit));
	}
}
