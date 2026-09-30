package com.grantfit.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class HealthService {

	private final HealthMapper healthMapper;

	public HealthService(HealthMapper healthMapper) {
		this.healthMapper = healthMapper;
	}

	public Map<String, Object> checkDatabase() {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("connected", healthMapper.selectOne() != null);
		result.put("database", healthMapper.selectCurrentDatabase());
		return result;
	}
}
