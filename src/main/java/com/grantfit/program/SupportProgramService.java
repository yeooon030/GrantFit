package com.grantfit.program;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SupportProgramService {

	// 마감일 판단은 한국 날짜 기준. DB(UTC)의 current_date 를 쓰지 않는다.
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final SupportProgramMapper supportProgramMapper;
	private final Clock clock;

	@Autowired
	public SupportProgramService(SupportProgramMapper supportProgramMapper) {
		this(supportProgramMapper, Clock.system(KST));
	}

	// 테스트에서 기준 시각을 고정하기 위한 생성자
	SupportProgramService(SupportProgramMapper supportProgramMapper, Clock clock) {
		this.supportProgramMapper = supportProgramMapper;
		this.clock = clock;
	}

	public SupportProgramPage findOpen(int page, int size) {
		LocalDate today = LocalDate.now(clock.withZone(KST));
		List<SupportProgramSummary> items =
				supportProgramMapper.selectOpenList(today, size, (long) page * size);
		long totalCount = supportProgramMapper.countOpen(today);
		return new SupportProgramPage(items, page, size, totalCount);
	}
}
