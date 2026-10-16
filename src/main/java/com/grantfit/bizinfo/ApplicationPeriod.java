package com.grantfit.bizinfo;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 기업마당 신청기간(reqstBeginEndDe) 문자열을 시작일/종료일로 나눈다.
 *
 * 실제 값 예: "2026-10-14 ~ 2026-10-29", "예산 소진시까지", "상시 접수"
 * 날짜 형식이 아니면 예외를 던지지 않고 두 값 모두 null 로 둔다.
 * 원문 문구는 raw_content 에 남는다.
 */
public record ApplicationPeriod(LocalDate start, LocalDate end) {

	private static final Pattern DATE_RANGE =
			Pattern.compile("^\\s*(\\d{4}-\\d{2}-\\d{2})\\s*~\\s*(\\d{4}-\\d{2}-\\d{2})\\s*$");

	private static final ApplicationPeriod UNKNOWN = new ApplicationPeriod(null, null);

	public static ApplicationPeriod parse(String text) {
		if (text == null) {
			return UNKNOWN;
		}
		Matcher matcher = DATE_RANGE.matcher(text);
		if (!matcher.matches()) {
			return UNKNOWN;
		}
		try {
			LocalDate start = LocalDate.parse(matcher.group(1));
			LocalDate end = LocalDate.parse(matcher.group(2));
			// DDL CHECK (start <= end) 위반 값은 신뢰할 수 없으므로 버린다.
			return start.isAfter(end) ? UNKNOWN : new ApplicationPeriod(start, end);
		} catch (DateTimeParseException e) {
			return UNKNOWN;
		}
	}
}
