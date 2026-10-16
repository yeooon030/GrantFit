package com.grantfit.bizinfo;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수집 실행용 관리 API. 로컬 개발용이며 인증이 없다.
 */
@RestController
@RequestMapping("/api/admin/bizinfo")
public class BizinfoCollectController {

	private final BizinfoCollectService bizinfoCollectService;

	public BizinfoCollectController(BizinfoCollectService bizinfoCollectService) {
		this.bizinfoCollectService = bizinfoCollectService;
	}

	@PostMapping("/collect")
	public CollectResult collect(
			@RequestParam(defaultValue = "1") int pageIndex,
			@RequestParam(defaultValue = "1") int pageUnit) {
		return bizinfoCollectService.collect(pageIndex, pageUnit);
	}

	@ExceptionHandler(BizinfoApiException.class)
	public ResponseEntity<Map<String, Object>> handleBizinfoApiException(BizinfoApiException e) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
	}
}
