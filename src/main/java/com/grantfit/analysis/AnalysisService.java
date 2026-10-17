package com.grantfit.analysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.grantfit.program.SupportProgram;
import com.grantfit.program.SupportProgramMapper;

/**
 * 공고 1건 AI 분석. (README §11, MILESTONE 2)
 *
 * 공고 원문 → Gemini → Structured JSON → DTO → Validation → ai_analysis
 *
 * Gemini 호출 동안 트랜잭션을 잡지 않는다. 상태 변경은 각각 바로 커밋된다.
 */
@Service
public class AnalysisService {

	private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

	private final SupportProgramMapper supportProgramMapper;
	private final AiAnalysisMapper aiAnalysisMapper;
	private final GeminiClient geminiClient;
	private final AnalysisPrompt analysisPrompt;
	private final AnalysisValidator analysisValidator;
	private final ObjectMapper objectMapper;
	private final ObjectReader resultReader;

	public AnalysisService(
			SupportProgramMapper supportProgramMapper,
			AiAnalysisMapper aiAnalysisMapper,
			GeminiClient geminiClient,
			AnalysisPrompt analysisPrompt,
			AnalysisValidator analysisValidator,
			ObjectMapper objectMapper) {
		this.supportProgramMapper = supportProgramMapper;
		this.aiAnalysisMapper = aiAnalysisMapper;
		this.geminiClient = geminiClient;
		this.analysisPrompt = analysisPrompt;
		this.analysisValidator = analysisValidator;
		this.objectMapper = objectMapper;
		// 스키마에 없는 필드가 오면 INVALID_JSON 으로 본다.
		this.resultReader = objectMapper.readerFor(AnalysisResult.class)
				.with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
	}

	public AnalysisOutcome analyze(long supportProgramId) {
		SupportProgram program = supportProgramMapper.selectById(supportProgramId);
		if (program == null) {
			throw new SupportProgramNotFoundException(supportProgramId);
		}

		// 같은 prompt_version 으로 완료된 결과가 있으면 재사용한다. (README §16)
		AiAnalysis existing = aiAnalysisMapper.selectByProgramAndVersion(supportProgramId, AnalysisPrompt.VERSION);
		if (existing != null && "COMPLETED".equals(existing.status())) {
			log.info("AI 분석 재사용 programId={} analysisId={}", supportProgramId, existing.id());
			return new AnalysisOutcome(existing, true);
		}

		long analysisId = aiAnalysisMapper.upsertAnalyzing(
				supportProgramId, geminiClient.model(), AnalysisPrompt.VERSION);
		try {
			AnalysisInput input = AnalysisInput.fromRawContent(program.rawContent(), objectMapper);
			GeminiClient.GeminiResponse response = geminiClient.generate(
					analysisPrompt.systemInstruction(), input.text(), analysisPrompt.responseSchema());

			AnalysisResult result = parse(response.text());

			List<String> errors = analysisValidator.validate(result, input.text());
			if (!errors.isEmpty()) {
				throw new AnalysisException(AnalysisErrorCode.VALIDATION_ERROR, String.join("\n", errors));
			}

			aiAnalysisMapper.updateCompleted(toRow(analysisId, response.modelVersion(), result));
			log.info("AI 분석 완료 programId={} analysisId={}", supportProgramId, analysisId);
		} catch (AnalysisException e) {
			aiAnalysisMapper.updateFailed(analysisId, e.getErrorCode().name(), e.getMessage());
			log.warn("AI 분석 실패 programId={} analysisId={} code={} message={}",
					supportProgramId, analysisId, e.getErrorCode(), e.getMessage());
		}
		return new AnalysisOutcome(aiAnalysisMapper.selectById(analysisId), false);
	}

	private AnalysisResult parse(String text) {
		try {
			return resultReader.readValue(text);
		} catch (JsonProcessingException e) {
			throw new AnalysisException(AnalysisErrorCode.INVALID_JSON,
					"응답 JSON 변환 실패: " + e.getOriginalMessage());
		}
	}

	private AiAnalysis toRow(long id, String model, AnalysisResult r) {
		Map<String, Object> eligibility = new LinkedHashMap<>();
		eligibility.put("regions", r.regions());
		eligibility.put("businessAge", r.businessAge());
		eligibility.put("applicantTypes", r.applicantTypes());
		eligibility.put("conditions", r.eligibility());

		return new AiAnalysis(
				id, null, model, null, null,
				json(eligibility),
				json(r.exclusions()),
				json(r.requiredDocuments()),
				json(r.support()),
				json(r.uncertainConditions()),
				json(r.evidence()),
				json(r.preferences()),
				r.summary(),
				null, null, null, null, null);
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("분석 결과 직렬화 실패", e);
		}
	}

	public record AnalysisOutcome(AiAnalysis analysis, boolean reused) {
	}
}
