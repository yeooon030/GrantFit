package com.grantfit.analysis;

import java.util.List;

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
	 * 이미 COMPLETED 이거나 다른 요청이 분석 중(ANALYZING)이면 아무것도 바꾸지 않는다.
	 * @return ai_analysis.id, 바꾸지 않았으면 null
	 */
	Long upsertAnalyzing(
			@Param("supportProgramId") long supportProgramId,
			@Param("model") String model,
			@Param("promptVersion") String promptVersion);

	int updateCompleted(AiAnalysis analysis);

	/**
	 * 해당 prompt_version 으로 COMPLETED 결과가 없는 공고 id. 일괄 분석 대상 (README §12)
	 */
	List<Long> selectProgramIdsToAnalyze(
			@Param("promptVersion") String promptVersion,
			@Param("limit") int limit);

	int updateFailed(
			@Param("id") long id,
			@Param("errorCode") String errorCode,
			@Param("errorMessage") String errorMessage,
			@Param("attemptCount") int attemptCount);
}
