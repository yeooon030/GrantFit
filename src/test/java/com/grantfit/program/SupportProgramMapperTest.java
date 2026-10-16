package com.grantfit.program;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

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

	@Test
	void 목록은_마감된_공고를_빼고_마감일순_신청기간_없는_공고는_뒤로() {
		// 실제 수집 데이터와 겹치지 않도록 기준일을 먼 미래로 둔다.
		// 기준일 이전에 마감된 실제 공고는 모두 빠지고, 신청기간 없는 실제 공고만 함께 조회된다.
		LocalDate today = LocalDate.of(2099, 1, 1);
		long before = supportProgramMapper.countOpen(today);

		supportProgramMapper.upsert(row("TEST_LIST_LATE", LocalDate.of(2099, 1, 10)));
		supportProgramMapper.upsert(row("TEST_LIST_SOON", LocalDate.of(2099, 1, 5)));
		supportProgramMapper.upsert(row("TEST_LIST_TODAY", today));
		supportProgramMapper.upsert(row("TEST_LIST_CLOSED", LocalDate.of(2098, 12, 31)));
		supportProgramMapper.upsert(row("TEST_LIST_ALWAYS", null));

		List<SupportProgramSummary> list = supportProgramMapper.selectOpenList(today, 100, 0);

		assertThat(supportProgramMapper.countOpen(today)).isEqualTo(before + 4);
		assertThat(list).extracting(SupportProgramSummary::title)
				.startsWith("TEST_LIST_TODAY", "TEST_LIST_SOON", "TEST_LIST_LATE", "TEST_LIST_ALWAYS")
				.doesNotContain("TEST_LIST_CLOSED");
		assertThat(list.subList(3, list.size())).allMatch(p -> p.applicationEnd() == null);
	}

	@Test
	void 목록_페이지는_limit과_offset을_따른다() {
		LocalDate today = LocalDate.of(2099, 1, 1);
		supportProgramMapper.upsert(row("TEST_PAGE_1", LocalDate.of(2099, 1, 2)));
		supportProgramMapper.upsert(row("TEST_PAGE_2", LocalDate.of(2099, 1, 3)));
		supportProgramMapper.upsert(row("TEST_PAGE_3", LocalDate.of(2099, 1, 4)));

		assertThat(supportProgramMapper.selectOpenList(today, 2, 0))
				.extracting(SupportProgramSummary::title).containsExactly("TEST_PAGE_1", "TEST_PAGE_2");
		assertThat(supportProgramMapper.selectOpenList(today, 2, 2))
				.extracting(SupportProgramSummary::title).first().isEqualTo("TEST_PAGE_3");
	}

	private SupportProgram row(String externalId, LocalDate end) {
		return new SupportProgram(
				null, externalId, externalId, "테스트기관", null, "중소기업",
				end == null ? null : end.minusDays(30), end, null, "https://www.bizinfo.go.kr/test",
				"{}", null, null);
	}

	private SupportProgram sample(LocalDate start, LocalDate end) {
		return new SupportProgram(
				null, EXTERNAL_ID, "테스트 공고", "테스트기관", null, "중소기업",
				start, end, null, "https://www.bizinfo.go.kr/test",
				"{\"pblancId\":\"TEST_MAPPER_0001\"}", null, null);
	}
}
