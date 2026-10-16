package com.grantfit.program;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 grantfit DB 에 INSERT/SELECT 한다.
 * @Transactional 로 테스트 종료 시 롤백되어 데이터가 남지 않는다.
 */
@SpringBootTest
@Transactional
class SupportProgramMapperTest {

	private static final String EXTERNAL_ID = "TEST_MAPPER_0001";

	@Autowired
	private SupportProgramMapper supportProgramMapper;

	@Test
	void INSERT한_공고를_external_id로_조회한다() {
		boolean inserted = supportProgramMapper.upsert(sample(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 29)));

		SupportProgram saved = supportProgramMapper.selectByExternalId(EXTERNAL_ID);

		assertThat(inserted).isTrue();
		assertThat(saved.id()).isNotNull();
		assertThat(saved.title()).isEqualTo("테스트 공고");
		assertThat(saved.organization()).isEqualTo("테스트기관");
		assertThat(saved.target()).isEqualTo("중소기업");
		assertThat(saved.applicationStart()).isEqualTo(LocalDate.of(2026, 10, 14));
		assertThat(saved.applicationEnd()).isEqualTo(LocalDate.of(2026, 10, 29));
		assertThat(saved.region()).isNull();
		assertThat(saved.supportAmount()).isNull();
		assertThat(saved.rawContent()).isEqualTo("{\"pblancId\":\"TEST_MAPPER_0001\"}");
		assertThat(saved.createdAt()).isNotNull();
		assertThat(saved.updatedAt()).isNotNull();
	}

	@Test
	void 신청기간이_없는_공고도_저장된다() {
		supportProgramMapper.upsert(sample(null, null));

		SupportProgram saved = supportProgramMapper.selectByExternalId(EXTERNAL_ID);

		assertThat(saved.applicationStart()).isNull();
		assertThat(saved.applicationEnd()).isNull();
	}

	@Test
	void 같은_external_id는_새_행을_만들지_않고_갱신한다() {
		supportProgramMapper.upsert(sample(null, null));
		SupportProgram first = supportProgramMapper.selectByExternalId(EXTERNAL_ID);

		boolean inserted = supportProgramMapper.upsert(new SupportProgram(
				null, EXTERNAL_ID, "수정된 공고", "수정기관", null, "소상공인",
				LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null,
				"https://www.bizinfo.go.kr/test2", "{\"pblancId\":\"TEST_MAPPER_0001\",\"v\":2}", null, null));
		SupportProgram second = supportProgramMapper.selectByExternalId(EXTERNAL_ID);

		assertThat(inserted).isFalse();
		assertThat(second.id()).isEqualTo(first.id());
		assertThat(second.createdAt()).isEqualTo(first.createdAt());
		assertThat(second.title()).isEqualTo("수정된 공고");
		assertThat(second.organization()).isEqualTo("수정기관");
		assertThat(second.target()).isEqualTo("소상공인");
		assertThat(second.applicationStart()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(second.applicationEnd()).isEqualTo(LocalDate.of(2026, 11, 30));
		assertThat(second.originalUrl()).isEqualTo("https://www.bizinfo.go.kr/test2");
		assertThat(second.rawContent()).contains("\"v\":2");
	}

	private SupportProgram sample(LocalDate start, LocalDate end) {
		return new SupportProgram(
				null, EXTERNAL_ID, "테스트 공고", "테스트기관", null, "중소기업",
				start, end, null, "https://www.bizinfo.go.kr/test",
				"{\"pblancId\":\"TEST_MAPPER_0001\"}", null, null);
	}
}
