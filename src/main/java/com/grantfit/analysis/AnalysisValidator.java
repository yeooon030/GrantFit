package com.grantfit.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.grantfit.analysis.AnalysisResult.Condition;

/**
 * Gemini 응답의 서버 측 검증. (README §3, §13)
 * Structured Output 은 JSON 형식만 보장하므로 값의 범위와 근거 문장을 직접 확인한다.
 */
@Component
public class AnalysisValidator {

	// 기업마당 hashtags 의 시·도 표기 (광주·전남은 "전남광주"로 통합) + 전국
	static final Set<String> REGIONS = Set.of(
			"서울", "부산", "대구", "인천", "전남광주", "대전", "울산", "세종",
			"경기", "강원", "충북", "충남", "전북", "경북", "경남", "제주", "전국");

	static final Set<String> APPLICANT_TYPES = Set.of("SOLE_PROPRIETOR", "CORPORATION", "PRE_FOUNDER");

	private static final int MAX_BUSINESS_AGE = 100;
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	/**
	 * @return 위반 사항 목록. 비어 있으면 통과
	 */
	public List<String> validate(AnalysisResult result, String inputText) {
		List<String> errors = new ArrayList<>();
		String normalizedInput = normalize(inputText);

		if (isBlank(result.summary())) {
			errors.add("summary 가 비어 있음");
		}

		requireList(errors, "regions", result.regions());
		if (result.regions() != null) {
			for (String region : result.regions()) {
				if (!REGIONS.contains(region)) {
					errors.add("regions 허용되지 않은 값: " + region);
				}
			}
			if (new HashSet<>(result.regions()).size() != result.regions().size()) {
				errors.add("regions 중복");
			}
		}

		requireList(errors, "applicantTypes", result.applicantTypes());
		if (result.applicantTypes() != null) {
			for (String type : result.applicantTypes()) {
				if (!APPLICANT_TYPES.contains(type)) {
					errors.add("applicantTypes 허용되지 않은 값: " + type);
				}
			}
		}

		if (result.businessAge() == null) {
			errors.add("businessAge 누락");
		} else {
			Integer min = result.businessAge().minYears();
			Integer max = result.businessAge().maxYears();
			checkRange(errors, "businessAge.minYears", min);
			checkRange(errors, "businessAge.maxYears", max);
			if (min != null && max != null && min > max) {
				errors.add("businessAge minYears > maxYears");
			}
		}

		if (result.support() == null) {
			errors.add("support 누락");
		} else if (result.support().maxAmount() != null && result.support().maxAmount() < 0) {
			errors.add("support.maxAmount 음수");
		}

		checkConditions(errors, "eligibility", result.eligibility(), normalizedInput);
		checkConditions(errors, "exclusions", result.exclusions(), normalizedInput);
		checkConditions(errors, "preferences", result.preferences(), normalizedInput);
		checkTexts(errors, "requiredDocuments", result.requiredDocuments());
		checkTexts(errors, "uncertainConditions", result.uncertainConditions());

		if (result.evidence() == null) {
			errors.add("evidence 누락");
		} else {
			checkEvidence(errors, "evidence.regions", result.evidence().regions(), normalizedInput);
			checkEvidence(errors, "evidence.businessAge", result.evidence().businessAge(), normalizedInput);
			checkEvidence(errors, "evidence.applicantTypes", result.evidence().applicantTypes(), normalizedInput);
			checkEvidence(errors, "evidence.support", result.evidence().support(), normalizedInput);
		}
		return errors;
	}

	private void checkConditions(List<String> errors, String name, List<Condition> conditions, String normalizedInput) {
		requireList(errors, name, conditions);
		if (conditions == null) {
			return;
		}
		for (int i = 0; i < conditions.size(); i++) {
			Condition c = conditions.get(i);
			String path = name + "[" + i + "]";
			if (c == null || isBlank(c.condition())) {
				errors.add(path + ".condition 이 비어 있음");
			}
			if (c == null || isBlank(c.evidence())) {
				errors.add(path + ".evidence 가 비어 있음");
			} else {
				checkEvidence(errors, path + ".evidence", c.evidence(), normalizedInput);
			}
		}
	}

	/**
	 * evidence 는 원문에서 그대로 복사해야 한다. (README §10)
	 * 줄바꿈·공백 차이만 허용하고, 원문에 없는 문장이면 위반이다.
	 */
	private void checkEvidence(List<String> errors, String path, String evidence, String normalizedInput) {
		if (evidence == null) {
			return;
		}
		if (isBlank(evidence)) {
			errors.add(path + " 가 빈 문자열");
		} else if (!normalizedInput.contains(normalize(evidence))) {
			errors.add(path + " 가 원문에 없음: " + abbreviate(evidence));
		}
	}

	private void checkTexts(List<String> errors, String name, List<String> texts) {
		requireList(errors, name, texts);
		if (texts != null && texts.stream().anyMatch(this::isBlank)) {
			errors.add(name + " 에 빈 값");
		}
	}

	private void checkRange(List<String> errors, String name, Integer value) {
		if (value != null && (value < 0 || value > MAX_BUSINESS_AGE)) {
			errors.add(name + " 범위 밖: " + value);
		}
	}

	private void requireList(List<String> errors, String name, List<?> list) {
		if (list == null) {
			errors.add(name + " 누락");
		}
	}

	static String normalize(String text) {
		return WHITESPACE.matcher(text).replaceAll("");
	}

	private boolean isBlank(String text) {
		return text == null || text.isBlank();
	}

	private static String abbreviate(String text) {
		return text.length() <= 50 ? text : text.substring(0, 50) + "…";
	}
}
