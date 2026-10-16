package com.grantfit.bizinfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 실제 기업마당 API 를 1건 호출한다.
 * BIZINFO_API_KEY 가 없으면 건너뛴다.
 */
@SpringBootTest
class BizinfoClientLiveTest {

	@Autowired
	private BizinfoClient bizinfoClient;

	@Value("${bizinfo.api-key:}")
	private String apiKey;

	@Test
	void 실제_API에서_공고_1건을_받아_매핑한다() {
		assumeTrue(!apiKey.isBlank(), "BIZINFO_API_KEY 미설정");

		List<BizinfoItem> items = bizinfoClient.fetch(1, 1);

		assertThat(items).hasSize(1);
		BizinfoItem item = items.get(0);
		assertThat(item.pblancId()).matches("PBLN_\\d+");
		assertThat(item.pblancNm()).isNotBlank();
		assertThat(item.pblancUrl()).startsWith("https://www.bizinfo.go.kr/");
		assertThat(item.rawJson()).contains("\"pblancId\"").doesNotContain(apiKey);
	}
}
