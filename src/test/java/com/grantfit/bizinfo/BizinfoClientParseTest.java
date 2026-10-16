package com.grantfit.bizinfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantfit.program.SupportProgram;

/**
 * 응답 JSON → DTO 매핑만 검증한다. 네트워크를 사용하지 않는다.
 * 샘플은 2026-10-17 실제 응답 구조를 기준으로 연락처 등을 가린 것이다.
 */
class BizinfoClientParseTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final BizinfoClient client =
			new BizinfoClient(RestClient.builder(), objectMapper, "http://localhost", "test-key");

	@Test
	void 응답_배열을_DTO로_매핑한다() throws IOException {
		List<BizinfoItem> items = client.parse(readSample());

		assertThat(items).hasSize(2);
		BizinfoItem first = items.get(0);
		assertThat(first.pblancId()).isEqualTo("PBLN_000000000127291");
		assertThat(first.pblancNm()).isEqualTo("[경남] 2026년 중소기업 청년지원사업 모집 공고");
		assertThat(first.jrsdInsttNm()).isEqualTo("경상남도");
		assertThat(first.trgetNm()).isEqualTo("중소기업");
		assertThat(first.reqstBeginEndDe()).isEqualTo("2026-10-14 ~ 2026-10-29");
		assertThat(first.pblancUrl()).startsWith("https://www.bizinfo.go.kr/");
	}

	@Test
	void 컬럼에_없는_필드는_rawJson에_원본_그대로_남는다() throws IOException {
		BizinfoItem first = client.parse(readSample()).get(0);

		JsonNode raw = objectMapper.readTree(first.rawJson());
		assertThat(raw.get("excInsttNm").asText()).isEqualTo("수행재단");
		assertThat(raw.get("reqstMthPapersCn").asText()).isEqualTo("방문 및 우편 접수");
		assertThat(raw.get("creatPnttm").asText()).isEqualTo("2026-10-16 14:04:07");
		assertThat(raw.get("updtPnttm").asText()).isEqualTo("2026-10-16 15:23:38");
		assertThat(raw.get("bsnsSumryCn").asText()).contains("<p>");
	}

	@Test
	void 빈_결과는_빈_목록() throws IOException {
		assertThat(client.parse(objectMapper.readTree("{\"jsonArray\":[]}"))).isEmpty();
	}

	@Test
	void reqErr_응답은_예외() throws IOException {
		JsonNode body = objectMapper.readTree("{\"reqErr\":\"존재하지 않는 인증키 입니다.\"}");

		assertThatThrownBy(() -> client.parse(body))
				.isInstanceOf(BizinfoApiException.class)
				.hasMessageContaining("존재하지 않는 인증키");
	}

	@Test
	void jsonArray가_없으면_예외() throws IOException {
		assertThatThrownBy(() -> client.parse(objectMapper.readTree("{}")))
				.isInstanceOf(BizinfoApiException.class);
	}

	@Test
	void DTO를_support_program_행으로_변환한다() throws IOException {
		SupportProgram program = client.parse(readSample()).get(0).toSupportProgram();

		assertThat(program.id()).isNull();
		assertThat(program.externalId()).isEqualTo("PBLN_000000000127291");
		assertThat(program.title()).isEqualTo("[경남] 2026년 중소기업 청년지원사업 모집 공고");
		assertThat(program.organization()).isEqualTo("경상남도");
		assertThat(program.target()).isEqualTo("중소기업");
		assertThat(program.applicationStart()).isEqualTo(LocalDate.of(2026, 10, 14));
		assertThat(program.applicationEnd()).isEqualTo(LocalDate.of(2026, 10, 29));
		assertThat(program.region()).isNull();
		assertThat(program.supportAmount()).isNull();
		assertThat(program.originalUrl()).startsWith("https://www.bizinfo.go.kr/");
		assertThat(program.rawContent()).contains("\"excInsttNm\":\"수행재단\"");
	}

	@Test
	void 날짜가_아닌_신청기간은_null로_변환한다() throws IOException {
		SupportProgram program = client.parse(readSample()).get(1).toSupportProgram();

		assertThat(program.applicationStart()).isNull();
		assertThat(program.applicationEnd()).isNull();
		assertThat(program.rawContent()).contains("예산 소진시까지");
	}

	private JsonNode readSample() throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/bizinfo/response-sample.json")) {
			return objectMapper.readTree(in);
		}
	}
}
