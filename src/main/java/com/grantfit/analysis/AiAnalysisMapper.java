package com.grantfit.analysis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiAnalysisMapper {

	AiAnalysis selectById(long id);

	AiAnalysis selectByProgramAndVersion(
			@Param("supportProgramId") long supportProgramId,
			@Param("promptVersion") String promptVersion);

	/**
	 * (공고, prompt_version) 행을 ANALYZING 으로 만든다. 없으면 INSERT, 있으면 이전 결과를 비운다.
	 * @return ai_analysis.id
	 */
	long upsertAnalyzing(
			@Param("supportProgramId") long supportProgramId,
			@Param("model") String model,
			@Param("promptVersion") String promptVersion);

	int updateCompleted(AiAnalysis analysis);

	int updateFailed(
			@Param("id") long id,
			@Param("errorCode") String errorCode,
			@Param("errorMessage") String errorMessage);
}
