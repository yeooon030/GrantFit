package com.grantfit.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.grantfit.analysis.AnalysisResult.BusinessAge;
import com.grantfit.analysis.AnalysisResult.Condition;
import com.grantfit.analysis.AnalysisResult.Evidence;
import com.grantfit.analysis.AnalysisResult.Support;

class AnalysisValidatorTest {

	private static final String INPUT = """
			[사업개요]
			광주광역시 관내 사업자등록증을 보유한
			중소 제조업체
			공고일 기준 업력 7년 이상의 중소기업
			국세 체납 기업은 제외
			""";

	private final AnalysisValidator validator = new AnalysisValidator();

	@Test
	void 정상_결과는_통과() {
		assertThat(validator.validate(valid(), INPUT)).isEmpty();
	}

	@Test
	void 줄바꿈과_공백_차이는_원문_일치로_본다() {
		AnalysisResult r = withEligibility(
				new Condition("광주 소재", "광주광역시 관내 사업자등록증을 보유한 중소 제조업체"));

		assertThat(validator.validate(r, INPUT)).isEmpty();
	}

	@Test
	void 원문에_없는_근거_문장은_위반() {
		AnalysisResult r = withEligibility(new Condition("광주 소재", "광주에 사업장을 둔 기업"));

		assertThat(validator.validate(r, INPUT)).singleElement().asString()
				.contains("eligibility[0].evidence 가 원문에 없음");
	}

	@Test
	void 허용되지_않은_지역은_위반() {
		AnalysisResult v = valid();
		AnalysisResult r = new AnalysisResult(v.summary(), List.of("광주"), v.businessAge(), v.applicantTypes(),
				v.support(), v.eligibility(), v.exclusions(), v.preferences(), v.requiredDocuments(),
				v.uncertainConditions(), v.evidence());

		assertThat(validator.validate(r, INPUT)).containsExactly("regions 허용되지 않은 값: 광주");
	}

	@Test
	void 업력_범위와_순서() {
		AnalysisResult v = valid();
		AnalysisResult r = new AnalysisResult(v.summary(), v.regions(), new BusinessAge(10, 3), v.applicantTypes(),
				new Support(-1L, null), v.eligibility(), v.exclusions(), v.preferences(), v.requiredDocuments(),
				v.uncertainConditions(), v.evidence());

		assertThat(validator.validate(r, INPUT))
				.containsExactlyInAnyOrder("businessAge minYears > maxYears", "support.maxAmount 음수");
	}

	@Test
	void 구조화_값의_근거도_원문과_대조한다() {
		AnalysisResult v = valid();
		AnalysisResult r = new AnalysisResult(v.summary(), v.regions(), v.businessAge(), v.applicantTypes(),
				v.support(), v.eligibility(), v.exclusions(), v.preferences(), v.requiredDocuments(),
				v.uncertainConditions(), new Evidence("광주광역시 관내", "창업 7년 이내", null, null));

		assertThat(validator.validate(r, INPUT)).singleElement().asString()
				.startsWith("evidence.businessAge 가 원문에 없음");
	}

	@Test
	void 필수_필드_누락() {
		AnalysisResult r = new AnalysisResult(null, null, null, null, null, null, null, null, null, null, null);

		assertThat(validator.validate(r, INPUT)).contains(
				"summary 가 비어 있음", "regions 누락", "businessAge 누락", "support 누락",
				"eligibility 누락", "evidence 누락");
	}

	private AnalysisResult valid() {
		return new AnalysisResult(
				"광주 중소 제조업체 온라인몰 입점 지원",
				List.of("전남광주"),
				new BusinessAge(7, null),
				List.of(),
				new Support(null, null),
				List.of(new Condition("중소 제조업체", "중소 제조업체")),
				List.of(new Condition("국세 체납", "국세 체납 기업은 제외")),
				List.of(),
				List.of("사업자등록증"),
				List.of(),
				new Evidence("광주광역시 관내", "공고일 기준 업력 7년 이상의 중소기업", null, null));
	}

	private AnalysisResult withEligibility(Condition condition) {
		AnalysisResult v = valid();
		return new AnalysisResult(v.summary(), v.regions(), v.businessAge(), v.applicantTypes(), v.support(),
				List.of(condition), v.exclusions(), v.preferences(), v.requiredDocuments(),
				v.uncertainConditions(), v.evidence());
	}
}
