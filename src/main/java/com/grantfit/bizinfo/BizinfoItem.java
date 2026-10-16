package com.grantfit.bizinfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.grantfit.program.SupportProgram;

/**
 * 기업마당 지원사업정보 API 응답의 공고 1건.
 * support_program 컬럼에 매핑되는 필드만 받는다.
 * 나머지 필드(수행기관, 신청방법, 문의처, 첨부파일, 등록일/수정일 등)는
 * rawJson 으로 원본 그대로 보존한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BizinfoItem(
		String pblancId,
		String pblancNm,
		String jrsdInsttNm,
		String trgetNm,
		String reqstBeginEndDe,
		String pblancUrl,
		String rawJson) {

	/**
	 * region, support_amount 는 API 에 해당 필드가 없어 null 로 둔다.
	 * (MILESTONE 2 이후 ai_analysis 가 담당)
	 */
	public SupportProgram toSupportProgram() {
		ApplicationPeriod period = ApplicationPeriod.parse(reqstBeginEndDe);
		return new SupportProgram(
				null,
				pblancId,
				pblancNm,
				jrsdInsttNm,
				null,
				trgetNm,
				period.start(),
				period.end(),
				null,
				pblancUrl,
				rawJson,
				null,
				null);
	}

	BizinfoItem withRawJson(String rawJson) {
		return new BizinfoItem(pblancId, pblancNm, jrsdInsttNm, trgetNm, reqstBeginEndDe, pblancUrl, rawJson);
	}
}
