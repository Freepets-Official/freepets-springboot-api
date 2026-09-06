package com.freepets.infra.oauth;

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
 * 액세스 토큰으로 소셜 userinfo API를 호출한다. 카카오·네이버가 공유한다.
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

        return readTree(uri, httpResponse.body());
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
