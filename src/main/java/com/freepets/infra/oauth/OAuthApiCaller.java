package com.freepets.infra.oauth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 소셜 제공자의 HTTP API를 호출한다. 카카오·네이버의 userinfo 조회와 애플의 토큰 교환·폐기가
 * 이 클래스를 공유한다.
 *
 * <p>{@code TourApiClient}와 같은 방식으로 {@link HttpClient}를 직접 쓴다.
 * 스프링에 의존하지 않는 POJO이고, {@link HttpClient}가 스레드 안전하므로 이 클래스도 안전하다.
 */
public class OAuthApiCaller {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OAuthApiCaller() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * @throws OAuthException 통신 실패(5xx 성격) 또는 토큰 거부(4xx 성격). 메시지에 상태코드를 남긴다
     */
    public JsonNode getWithBearerToken(
            String uri,
            String accessToken
    ) {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(uri))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        return readTree(uri, sendExpectingOk(uri, httpRequest));
    }

    /**
     * 폼 인코딩 POST 후 JSON 응답을 돌려준다. 애플 토큰 교환({@code /auth/token})이 쓴다.
     *
     * @throws OAuthException 통신 실패 또는 200이 아닌 응답
     */
    public JsonNode postFormForJson(
            String uri,
            Map<String, String> formParameters
    ) {
        return readTree(uri, sendExpectingOk(uri, formRequest(uri, formParameters)));
    }

    /**
     * 폼 인코딩 POST를 보내고 응답 본문은 버린다. 애플 토큰 폐기({@code /auth/revoke})가 쓴다 —
     * 성공하면 200에 본문이 비어 있어서, JSON 파싱을 강제하면 정상 응답에서 오히려 실패한다.
     *
     * @throws OAuthException 통신 실패 또는 200이 아닌 응답
     */
    public void postForm(
            String uri,
            Map<String, String> formParameters
    ) {
        sendExpectingOk(uri, formRequest(uri, formParameters));
    }

    private HttpRequest formRequest(
            String uri,
            Map<String, String> formParameters
    ) {
        String body = formParameters.entrySet().stream()
                .map(parameter -> encode(parameter.getKey()) + "=" + encode(parameter.getValue()))
                .collect(Collectors.joining("&"));

        return HttpRequest.newBuilder(URI.create(uri))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** 전송과 상태코드 판정을 한자리에 모은다. */
    private String sendExpectingOk(
            String uri,
            HttpRequest httpRequest
    ) {
        HttpResponse<String> httpResponse;
        try {
            httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new OAuthException(uri + " 호출에 실패했습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OAuthException(uri + " 호출이 중단되었습니다.", exception);
        }

        int statusCode = httpResponse.statusCode();
        if (statusCode != 200) {
            throw new OAuthException(
                    uri + " 호출이 거부되었습니다. HTTP " + statusCode + " body=" + httpResponse.body()
            );
        }

        return httpResponse.body();
    }

    private JsonNode readTree(
            String uri,
            String body
    ) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new OAuthException(uri + " 응답을 JSON으로 읽지 못했습니다.", exception);
        }
    }
}
