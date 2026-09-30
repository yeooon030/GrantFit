package com.grantfit.health;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthMapper {

	/**
	 * DB 연결과 MyBatis 매핑이 동작하는지 확인한다.
	 * 실제 도메인 테이블에 의존하지 않도록 상수만 조회한다.
	 */
	Integer selectOne();

	/**
	 * 현재 접속한 데이터베이스 이름을 조회한다.
	 */
	String selectCurrentDatabase();
}
