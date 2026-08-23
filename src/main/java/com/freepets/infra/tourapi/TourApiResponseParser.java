package com.freepets.infra.tourapi;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 관광공사 응답 JSON에서 항목 목록과 전체 건수를 꺼낸다.
 *
 * <p>공공데이터포털 응답은 구조가 유동적이다. 데이터가 없으면 {@code items}가 빈 문자열로 오고,
 * 결과가 한 건이면 {@code item}이 배열이 아니라 객체로 온다. 두 경우를 모두 흡수한다.
 */
@Component
public class TourApiResponseParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public <T> List<T> parseItems(
            String responseBody,
            Class<T> itemType
    ) {
        JsonNode items = readTree(responseBody)
                .path("response")
                .path("body")
                .path("items");

        if (!items.isObject()) {
            return List.of();
        }

        JsonNode item = items.path("item");
        List<T> parsed = new ArrayList<>();

        if (item.isArray()) {
            item.forEach(element -> parsed.add(convert(element, itemType)));
        } else if (item.isObject()) {
            parsed.add(convert(item, itemType));
        }
        return parsed;
    }

    public int parseTotalCount(String responseBody) {
        return readTree(responseBody)
                .path("response")
                .path("body")
                .path("totalCount")
                .asInt(0);
    }

    private <T> T convert(
            JsonNode node,
            Class<T> itemType
    ) {
        return objectMapper.convertValue(node, itemType);
    }

    private JsonNode readTree(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new TourApiException("관광공사 응답을 해석할 수 없습니다.", exception);
        }
    }

}
