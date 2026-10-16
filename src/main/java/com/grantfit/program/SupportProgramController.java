package com.grantfit.program;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support-programs")
public class SupportProgramController {

	private static final int MAX_SIZE = 100;

	private final SupportProgramService supportProgramService;

	public SupportProgramController(SupportProgramService supportProgramService) {
		this.supportProgramService = supportProgramService;
	}

	/**
	 * 마감되지 않은 지원사업 목록. (README §20.2, MILESTONE 1)
	 * 마감일 오름차순, 신청기간이 없는 공고는 뒤로 정렬한다.
	 */
	@GetMapping
	public SupportProgramPage list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		if (page < 0) {
			throw new IllegalArgumentException("page 는 0 이상이어야 합니다.");
		}
		if (size < 1 || size > MAX_SIZE) {
			throw new IllegalArgumentException("size 는 1~" + MAX_SIZE + " 이어야 합니다.");
		}
		return supportProgramService.findOpen(page, size);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
	}
}
