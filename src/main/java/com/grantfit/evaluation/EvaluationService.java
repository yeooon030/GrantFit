package com.grantfit.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantfit.analysis.AiAnalysis;
import com.grantfit.analysis.AiAnalysisMapper;
import com.grantfit.analysis.AiAnalysisMapper.StatusCount;
import com.grantfit.evaluation.EvaluationReport.Mismatch;
import com.grantfit.evaluation.EvaluationReport.Score;
import com.grantfit.program.SupportProgram;
import com.grantfit.program.SupportProgramMapper;

/**
 * Golden Dataset 과 ai_analysis 를 비교해 정확도를 측정한다. (README §13, §14, MILESTONE 4)
 *
 * - 비교 대상: labeled=true 이고 해당 prompt_version 분석이 COMPLETED 인 공고
 * - 지역·기업형태는 집합 일치, 업력·지원금은 값 일치 (null = null 도 일치)
 * - 신청기간은 AI 가 아니라 Java 파싱 결과(support_program)와 비교한다
 * - Gemini 를 호출하지 않는다
 */
@Service
public class EvaluationService {

	static final List<String> AI_FIELDS = List.of("regions", "businessAge", "maxAmount", "applicantTypes");

	private final SupportProgramMapper supportProgramMapper;
	private final AiAnalysisMapper aiAnalysisMapper;
	private final ObjectMapper objectMapper;
	private final Path goldenPath;

	public EvaluationService(
			SupportProgramMapper supportProgramMapper,
			AiAnalysisMapper aiAnalysisMapper,
			ObjectMapper objectMapper,
			@Value("${evaluation.golden-path:evaluation/golden-dataset.json}") String goldenPath) {
		this.supportProgramMapper = supportProgramMapper;
		this.aiAnalysisMapper = aiAnalysisMapper;
		this.objectMapper = objectMapper;
		this.goldenPath = Path.of(goldenPath);
	}

	public EvaluationReport evaluate(String promptVersion) {
		GoldenDataset golden = loadGolden();

		Map<String, int[]> fieldCounts = new LinkedHashMap<>();
		AI_FIELDS.forEach(f -> fieldCounts.put(f, new int[2]));
		int[] period = new int[2];
		int[] evidence = new int[2];
		int labeled = 0;
		int evaluated = 0;
		List<String> notAnalyzed = new ArrayList<>();
		List<Mismatch> mismatches = new ArrayList<>();

		for (GoldenDataset.Item g : golden.items()) {
			if (!g.labeled()) {
				continue;
			}
			labeled++;
			SupportProgram program = supportProgramMapper.selectByExternalId(g.externalId());
			AiAnalysis analysis = program == null ? null
					: aiAnalysisMapper.selectByProgramAndVersion(program.id(), promptVersion);
			if (analysis == null || !"COMPLETED".equals(analysis.status())) {
				notAnalyzed.add(g.externalId());
				continue;
			}
			evaluated++;

			Extracted ai = extract(analysis);
			compare(fieldCounts, mismatches, g, "regions", asSet(g.regions()), asSet(ai.regions()));
			compare(fieldCounts, mismatches, g, "businessAge", ageOf(g.businessAge()), ai.businessAge());
			compare(fieldCounts, mismatches, g, "maxAmount", g.maxAmount(), ai.maxAmount());
			compare(fieldCounts, mismatches, g, "applicantTypes", asSet(g.applicantTypes()), asSet(ai.applicantTypes()));

			period[1]++;
			List<Object> expectedPeriod = Arrays.asList(g.applicationStart(), g.applicationEnd());
			List<Object> actualPeriod = Arrays.asList(program.applicationStart(), program.applicationEnd());
			if (expectedPeriod.equals(actualPeriod)) {
				period[0]++;
			} else {
				mismatches.add(new Mismatch(g.externalId(), g.title(), "applicationPeriod(rule)", expectedPeriod, actualPeriod));
			}

			countEvidence(evidence, !ai.regions().isEmpty(), ai.evidence("regions"));
			countEvidence(evidence, ai.businessAge().get(0) != null || ai.businessAge().get(1) != null, ai.evidence("businessAge"));
			countEvidence(evidence, ai.maxAmount() != null, ai.evidence("support"));
			countEvidence(evidence, !ai.applicantTypes().isEmpty(), ai.evidence("applicantTypes"));
		}

		Map<String, Score> fields = new LinkedHashMap<>();
		int mainCorrect = 0;
		int mainTotal = 0;
		for (Map.Entry<String, int[]> e : fieldCounts.entrySet()) {
			fields.put(e.getKey(), Score.of(e.getValue()[0], e.getValue()[1]));
			mainCorrect += e.getValue()[0];
			mainTotal += e.getValue()[1];
		}

		return new EvaluationReport(promptVersion, golden.items().size(), labeled, evaluated, notAnalyzed,
				fields, Score.of(mainCorrect, mainTotal), Score.of(period[0], period[1]),
				Score.of(evidence[0], evidence[1]), structuredOutput(promptVersion), mismatches);
	}

	private void compare(Map<String, int[]> counts, List<Mismatch> mismatches,
			GoldenDataset.Item g, String field, Object expected, Object actual) {
		int[] c = counts.get(field);
		c[1]++;
		if (Objects.equals(expected, actual)) {
			c[0]++;
		} else {
			mismatches.add(new Mismatch(g.externalId(), g.title(), field, expected, actual));
		}
	}

	private void countEvidence(int[] counts, boolean extracted, String evidence) {
		if (!extracted) {
			return;
		}
		counts[1]++;
		if (evidence != null && !evidence.isBlank()) {
			counts[0]++;
		}
	}

	private EvaluationReport.StructuredOutput structuredOutput(String promptVersion) {
		int completed = 0;
		int invalidJson = 0;
		int validationError = 0;
		int infra = 0;
		for (StatusCount sc : aiAnalysisMapper.countByStatus(promptVersion)) {
			if ("COMPLETED".equals(sc.status())) {
				completed += sc.count();
			} else if ("INVALID_JSON".equals(sc.errorCode())) {
				invalidJson += sc.count();
			} else if ("VALIDATION_ERROR".equals(sc.errorCode())) {
				validationError += sc.count();
			} else if ("FAILED".equals(sc.status())) {
				infra += sc.count();
			}
		}
		int denominator = completed + invalidJson + validationError;
		Double rate = denominator == 0 ? null : Math.round(completed * 1000.0 / denominator) / 1000.0;
		return new EvaluationReport.StructuredOutput(completed, invalidJson, validationError, infra, rate);
	}

	private Extracted extract(AiAnalysis a) {
		JsonNode eligibility = read(a.eligibilityJson());
		JsonNode support = read(a.supportAmountJson());
		JsonNode evidence = read(a.evidenceJson());
		JsonNode age = eligibility.path("businessAge");
		return new Extracted(
				strings(eligibility.path("regions")),
				Arrays.asList(intOrNull(age.path("minYears")), intOrNull(age.path("maxYears"))),
				support.path("maxAmount").isNumber() ? support.path("maxAmount").asLong() : null,
				strings(eligibility.path("applicantTypes")),
				evidence);
	}

	private GoldenDataset loadGolden() {
		try {
			return objectMapper.readValue(Files.readString(goldenPath), GoldenDataset.class);
		} catch (IOException e) {
			throw new IllegalStateException("Golden Dataset 을 읽을 수 없습니다: " + goldenPath, e);
		}
	}

	private JsonNode read(String json) {
		try {
			return json == null ? objectMapper.createObjectNode() : objectMapper.readTree(json);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("저장된 분석 결과를 읽을 수 없습니다.", e);
		}
	}

	private static List<Object> ageOf(GoldenDataset.BusinessAge age) {
		return age == null ? Arrays.asList(null, null)
				: Arrays.asList(age.minYears(), age.maxYears());
	}

	private static Integer intOrNull(JsonNode node) {
		return node.isNumber() ? node.asInt() : null;
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(n -> values.add(n.asText()));
		return values;
	}

	private static Set<String> asSet(List<String> values) {
		return values == null ? Set.of() : new HashSet<>(values);
	}

	private record Extracted(
			List<String> regions,
			List<Object> businessAge,
			Long maxAmount,
			List<String> applicantTypes,
			JsonNode evidenceNode) {

		String evidence(String field) {
			JsonNode n = evidenceNode.path(field);
			return n.isTextual() ? n.asText() : null;
		}
	}
}
