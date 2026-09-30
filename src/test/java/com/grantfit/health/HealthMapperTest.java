package com.grantfit.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 실제 PostgreSQL 에 연결되는지, MyBatis XML 매핑이 동작하는지 확인한다.
 * 로컬에 DB 가 준비되어 있어야 한다. (.env 참고)
 */
@SpringBootTest
class HealthMapperTest {

	@Autowired
	private HealthMapper healthMapper;

	@Test
	void DB에_연결되어_쿼리를_실행한다() {
		assertThat(healthMapper.selectOne()).isEqualTo(1);
	}

	@Test
	void 설정된_데이터베이스에_접속한다() {
		assertThat(healthMapper.selectCurrentDatabase()).isEqualTo("grantfit");
	}
}
