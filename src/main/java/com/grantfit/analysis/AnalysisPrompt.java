package com.grantfit.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 현재 사용하는 프롬프트와 응답 스키마. (README §15)
 * 프롬프트나 스키마를 바꾸면 새 버전 파일을 만들고 VERSION 을 올린다.
 */
@Component
public class AnalysisPrompt {

	public static final String VERSION = "v1";

	private final String systemInstruction;
	private final JsonNode responseSchema;

	public AnalysisPrompt(ObjectMapper objectMapper) throws IOException {
		this.systemInstruction = read("ai/analysis-prompt-" + VERSION + ".txt");
		this.responseSchema = objectMapper.readTree(read("ai/analysis-schema-" + VERSION + ".json"));
	}

	public String systemInstruction() {
		return systemInstruction;
	}

	public JsonNode responseSchema() {
		return responseSchema;
	}

	private static String read(String path) throws IOException {
		try (InputStream in = new ClassPathResource(path).getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
