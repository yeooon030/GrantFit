package com.grantfit.program;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class SupportProgramServiceTest {

	private final SupportProgramMapper mapper = mock(SupportProgramMapper.class);

	@Test
	void 마감_기준일은_한국_날짜를_사용한다() {
		// UTC 2026-10-16 16:00 = KST 2026-10-17 01:00
		Clock utcClock = Clock.fixed(Instant.parse("2026-10-16T16:00:00Z"), ZoneOffset.UTC);
		SupportProgramService service = new SupportProgramService(mapper, utcClock);

		service.findOpen(0, 20);

		verify(mapper).selectOpenList(eq(LocalDate.of(2026, 10, 17)), anyInt(), anyLong());
		verify(mapper).countOpen(LocalDate.of(2026, 10, 17));
	}

	@Test
	void page와_size로_offset을_계산한다() {
		SupportProgramService service = new SupportProgramService(mapper, Clock.systemUTC());

		service.findOpen(3, 20);

		verify(mapper).selectOpenList(org.mockito.ArgumentMatchers.any(), eq(20), eq(60L));
	}
}
