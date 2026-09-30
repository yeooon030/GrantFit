package com.grantfit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 스프링 컨텍스트가 정상적으로 기동되는지 확인한다.
 * DataSource, MyBatis 설정에 문제가 있으면 이 테스트에서 먼저 실패한다.
 */
@SpringBootTest
class GrantFitApplicationTests {

	@Test
	void contextLoads() {
	}
}
