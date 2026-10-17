package com.grantfit.analysis;

import java.util.regex.Pattern;

import org.springframework.web.util.HtmlUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Gemini 에 보낼 공고 텍스트. (README §8 AI 입력)
 * support_program.raw_content(기업마당 item JSON)에서 필요한 항목만 꺼내 구성한다.
 * 서버 Validation 의 evidence 원문 대조도 이 텍스트를 기준으로 한다.
 */
public record AnalysisInput(String text) {

	private static final Pattern LINE_BREAK_TAGS = Pattern.compile("(?i)<br\\s*/?>|</p>|</div>|</li>|</tr>");
	private static final Pattern TAGS = Pattern.compile("<[^>]+>");
	private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0]+");
	private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n+");

	public static AnalysisInput fromRawContent(String rawContent, ObjectMapper objectMapper) {
		JsonNode raw;
		try {
			raw = objectMapper.readTree(rawContent);
		} catch (JsonProcessingException | IllegalArgumentException e) {
			throw new IllegalStateException("raw_content 를 읽을 수 없습니다.", e);
		}
		String text = "[공고명]\n" + field(raw, "pblancNm")
				+ "\n[소관기관]\n" + field(raw, "jrsdInsttNm")
				+ "\n[사업개요]\n" + cleanHtml(field(raw, "bsnsSumryCn"))
				+ "\n[지원대상]\n" + field(raw, "trgetNm")
				+ "\n[신청기간]\n" + field(raw, "reqstBeginEndDe")
				+ "\n[신청방법]\n" + cleanHtml(field(raw, "reqstMthPapersCn"));
		return new AnalysisInput(text);
	}

	static String cleanHtml(String html) {
		String text = LINE_BREAK_TAGS.matcher(html).replaceAll("\n");
		text = TAGS.matcher(text).replaceAll("");
		text = HtmlUtils.htmlUnescape(text).replace("\r", "");
		text = SPACES.matcher(text).replaceAll(" ");
		return BLANK_LINES.matcher(text).replaceAll("\n").strip();
	}

	private static String field(JsonNode raw, String name) {
		JsonNode node = raw.get(name);
		return node == null || node.isNull() ? "" : node.asText();
	}
}
