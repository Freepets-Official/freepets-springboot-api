package com.freepets.infra.geocoding;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 카카오 로컬 API(주소 검색)에 GET을 보내고 응답을 읽는다.
 *
 * <p>{@code NtsApiCaller}·{@code OAuthApiCaller}와 같은 자리다 — 전송만 담당하고 판정은
 * {@link KakaoGeocodingClient}가 한다.
 *
 * <p>스프링에 의존하지 않는 POJO이고, {@link HttpClient}가 스레드 안전하므로 이 클래스도 안전하다.
 */
public class KakaoGeocodingApiCaller {

    private static final String ADDRESS_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/address.json";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KakaoGeocodingApiCaller() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * @throws GeocodingException 통신 실패, 거부 응답(HTTP 200이 아님), JSON으로 읽지 못한 응답
     */
    public JsonNode searchAddress(
            String address,
            String restApiKey
    ) {
        String uri = ADDRESS_SEARCH_URL + "?query=" + URLEncoder.encode(address, StandardCharsets.UTF_8);

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(uri))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "KakaoAK " + restApiKey)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> httpResponse;
        try {
            httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new GeocodingException("카카오 로컬 API 호출에 실패했습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeocodingException("카카오 로컬 API 호출이 중단되었습니다.", exception);
        }

        int statusCode = httpResponse.statusCode();
        if (statusCode != 200) {
            // 주소를 로그에 남기면 사업자의 매장 위치가 노출되므로 상태코드만 남긴다.
            throw new GeocodingException("카카오 로컬 API 호출이 거부되었습니다. HTTP " + statusCode);
        }

        return readTree(httpResponse.body());
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new GeocodingException("카카오 로컬 API 응답을 JSON으로 읽지 못했습니다.", exception);
        }
    }
}
