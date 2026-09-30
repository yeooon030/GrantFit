package com.grantfit.health;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

	private final HealthService healthService;

	public HealthController(HealthService healthService) {
		this.healthService = healthService;
	}

	@GetMapping
	public Map<String, Object> health() {
		return Map.of("status", "UP");
	}

	@GetMapping("/db")
	public Map<String, Object> databaseHealth() {
		return healthService.checkDatabase();
	}
}
