package com.grantfit.bizinfo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 기업마당 지원사업정보 API 클라이언트.
 *
 * GET https://www.bizinfo.go.kr/uss/rss/bizinfoApi.do
 *   crtfcKey, dataType=json, pageUnit, pageIndex (pageUnit 은 pageIndex 와 함께 보내야 한다)
 *
 * 응답: 성공 {"jsonArray": [ {...}, ... ]}, 결과 없음 {"jsonArray": []}, 오류 {"reqErr": "..."}
 */
@Component
public class BizinfoClient {

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final String apiKey;

	public BizinfoClient(
			RestClient.Builder restClientBuilder,
			ObjectMapper objectMapper,
			@Value("${bizinfo.base-url}") String baseUrl,
			@Value("${bizinfo.api-key:}") String apiKey) {
		this.restClient = restClientBuilder.baseUrl(baseUrl).build();
		this.objectMapper = objectMapper;
		this.apiKey = apiKey;
	}

	public List<BizinfoItem> fetch(int pageIndex, int pageUnit) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new BizinfoApiException("BIZINFO_API_KEY 가 설정되지 않았습니다.");
		}

		JsonNode body;
		try {
			body = restClient.get()
					.uri(uri -> uri
							.queryParam("crtfcKey", apiKey)
							.queryParam("dataType", "json")
							.queryParam("pageUnit", pageUnit)
							.queryParam("pageIndex", pageIndex)
							.build())
					.retrieve()
					.body(JsonNode.class);
		} catch (RestClientException e) {
			// 예외 메시지에 요청 URL(인증키 포함)이 섞일 수 있어 원인 메시지를 그대로 노출하지 않는다.
			throw new BizinfoApiException("기업마당 API 호출 실패: " + e.getClass().getSimpleName());
		}

		return parse(body);
	}

	List<BizinfoItem> parse(JsonNode body) {
		if (body == null) {
			throw new BizinfoApiException("기업마당 API 응답 본문이 비어 있습니다.");
		}
		if (body.hasNonNull("reqErr")) {
			throw new BizinfoApiException("기업마당 API 오류: " + body.get("reqErr").asText());
		}
		JsonNode array = body.get("jsonArray");
		if (array == null || !array.isArray()) {
			throw new BizinfoApiException("기업마당 API 응답에 jsonArray 가 없습니다.");
		}

		List<BizinfoItem> items = new ArrayList<>(array.size());
		for (JsonNode node : array) {
			try {
				BizinfoItem item = objectMapper.treeToValue(node, BizinfoItem.class);
				items.add(item.withRawJson(objectMapper.writeValueAsString(node)));
			} catch (JsonProcessingException e) {
				throw new BizinfoApiException("기업마당 공고 매핑 실패", e);
			}
		}
		return items;
	}
}
