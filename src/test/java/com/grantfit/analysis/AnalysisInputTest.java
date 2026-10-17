package com.grantfit.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class AnalysisInputTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void HTML_태그를_줄바꿈과_텍스트로_정리한다() {
		String html = "<p>첫 문장입니다.</p><p><br></p><p style=\"line-height: 1.8;\">☞&nbsp;둘째&amp;문장</p>";

		assertThat(AnalysisInput.cleanHtml(html)).isEqualTo("첫 문장입니다.\n☞ 둘째&문장");
	}

	@Test
	void 줄바꿈_CR과_연속_공백을_정리한다() {
		assertThat(AnalysisInput.cleanHtml("방문  접수\r\n\r\n- 접수처 : 김해")).isEqualTo("방문 접수\n- 접수처 : 김해");
	}

	@Test
	void raw_content에서_README_8의_입력_항목으로_구성한다() throws IOException {
		AnalysisInput input = AnalysisInput.fromRawContent(readSample(), objectMapper);

		assertThat(input.text())
				.startsWith("[공고명]\n[전남광주] 2026년 중소기업 온라인몰 입점 지원사업")
				.contains("[소관기관]\n전남광주통합특별시")
				.contains("[사업개요]\n")
				.contains("[지원대상]\n")
				.contains("[신청기간]\n")
				.contains("[신청방법]\n")
				.doesNotContain("<p>", "&nbsp;");
	}

	private String readSample() throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/analysis/raw-content-sample.json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
