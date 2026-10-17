package com.grantfit.program;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SupportProgramMapper {

	/**
	 * external_id 기준 UPSERT.
	 * @return 새로 INSERT 됐으면 true, 기존 공고를 갱신했으면 false
	 */
	boolean upsert(SupportProgram supportProgram);

	SupportProgram selectById(long id);

	SupportProgram selectByExternalId(String externalId);

	/**
	 * 마감되지 않은 공고 목록. 마감일 오름차순, 신청기간이 없는 공고는 뒤로.
	 * @param today 마감 판단 기준일 (DB 가 UTC 이므로 호출 측에서 KST 기준으로 넘긴다)
	 */
	List<SupportProgramSummary> selectOpenList(
			@Param("today") LocalDate today,
			@Param("limit") int limit,
			@Param("offset") long offset);

	long countOpen(@Param("today") LocalDate today);
}
