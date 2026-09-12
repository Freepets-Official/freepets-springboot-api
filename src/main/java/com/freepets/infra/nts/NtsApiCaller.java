package com.freepets.infra.nts;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 국세청 오픈API에 JSON을 POST하고 응답을 읽는다.
 *
 * <p>{@code OAuthApiCaller}와 같은 자리다 — 전송만 담당하고 판정은 {@link NtsClient}가 한다.
 * 이렇게 갈라두면 클라이언트 로직을 네트워크 없이 테스트할 수 있다.
 *
 * <p>스프링에 의존하지 않는 POJO이고, {@link HttpClient}가 스레드 안전하므로 이 클래스도 안전하다.
 */
public class NtsApiCaller {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NtsApiCaller() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * @throws NtsException 통신 실패, 거부 응답(HTTP 200이 아님), JSON으로 읽지 못한 응답
     */
    public JsonNode postJson(
            String uri,
            String requestBody
    ) {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(uri))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> httpResponse;
        try {
            httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new NtsException("국세청 API 호출에 실패했습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NtsException("국세청 API 호출이 중단되었습니다.", exception);
        }

        String body = httpResponse.body();
        verifyNotErrorResponse(httpResponse.statusCode(), body);
        return readTree(body);
    }

    /**
     * 공공데이터포털은 JSON을 요청해도 인증 실패·한도 초과 시 XML로 응답한다. JSON 파서에 그대로
     * 넘기면 원인 불명의 예외가 나므로 여기서 먼저 걸러낸다({@code TourApiClient}와 같은 방어).
     *
     * <p>예외 메시지에 응답 본문을 담지 않는다. 국세청 오류 응답은 요청 항목을 되돌려주기도 해서
     * 사업자등록번호·대표자 성명이 로그에 남을 수 있다. XML 오류는 포털이 만든 것이라 사업자
     * 정보가 없으므로, 원인 코드만 뽑아 담는다.
     */
    private void verifyNotErrorResponse(
            int statusCode,
            String body
    ) {
        if (body == null || body.isBlank()) {
            throw new NtsException("국세청 API 응답이 비어 있습니다. (HTTP " + statusCode + ")");
        }

        String trimmed = body.stripLeading();
        if (trimmed.startsWith("<")) {
            throw new NtsException(
                    "국세청 API 호출이 거부되었습니다."
                            + " reasonCode=" + extractXmlTagValue(trimmed, "returnReasonCode")
                            + ", authMsg=" + extractXmlTagValue(trimmed, "returnAuthMsg")
                            + ", errMsg=" + extractXmlTagValue(trimmed, "errMsg")
                            + " (30=서비스키 오류, 22=일일 한도 초과, 32=미등록 IP)"
            );
        }

        if (statusCode != 200) {
            throw new NtsException(
                    "국세청 API 호출이 거부되었습니다. HTTP " + statusCode
                            + " (400=JSON 포맷 오류, 411=필수 항목 누락, 413=100건 초과, 500=국세청 내부 오류)"
            );
        }
    }

    private String extractXmlTagValue(
            String xml,
            String tagName
    ) {
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";

        int start = xml.indexOf(openTag);
        if (start < 0) {
            return "-";
        }
        int end = xml.indexOf(closeTag, start);
        if (end < 0) {
            return "-";
        }
        return xml.substring(start + openTag.length(), end).trim();
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new NtsException("국세청 API 응답을 JSON으로 읽지 못했습니다.", exception);
        }
    }
}
