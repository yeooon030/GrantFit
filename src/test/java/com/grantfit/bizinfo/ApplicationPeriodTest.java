package com.grantfit.bizinfo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationPeriodTest {

	@Test
	void 날짜_범위를_시작일과_종료일로_나눈다() {
		ApplicationPeriod period = ApplicationPeriod.parse("2026-10-14 ~ 2026-10-29");

		assertThat(period.start()).isEqualTo(LocalDate.of(2026, 10, 14));
		assertThat(period.end()).isEqualTo(LocalDate.of(2026, 10, 29));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {
			"예산 소진시까지",
			"상시 접수",
			"모집 완료시까지",
			"",
			"2026-02-30 ~ 2026-03-10",   // 존재하지 않는 날짜
			"2026-10-29 ~ 2026-10-14",   // 시작일이 종료일보다 늦음
			"20261014 ~ 20261029"        // 실제 응답에 없는 형식
	})
	void 날짜_범위가_아니면_둘_다_null(String text) {
		ApplicationPeriod period = ApplicationPeriod.parse(text);

		assertThat(period.start()).isNull();
		assertThat(period.end()).isNull();
	}
}
