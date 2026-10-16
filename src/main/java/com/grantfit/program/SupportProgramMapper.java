package com.grantfit.program;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SupportProgramMapper {

	/**
	 * external_id 기준 UPSERT.
	 * @return 새로 INSERT 됐으면 true, 기존 공고를 갱신했으면 false
	 */
	boolean upsert(SupportProgram supportProgram);

	SupportProgram selectByExternalId(String externalId);
}
