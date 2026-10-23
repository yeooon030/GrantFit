package com.grantfit.evaluation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grantfit.analysis.AnalysisPrompt;

/**
 * AI 정확도 측정. 로컬 개발용이며 인증이 없다. (MILESTONE 4)
 */
@RestController
@RequestMapping("/api/admin/evaluation")
public class EvaluationController {

	private final EvaluationService evaluationService;

	public EvaluationController(EvaluationService evaluationService) {
		this.evaluationService = evaluationService;
	}

	@GetMapping
	public EvaluationReport evaluate(@RequestParam(defaultValue = AnalysisPrompt.VERSION) String promptVersion) {
		return evaluationService.evaluate(promptVersion);
	}
}
