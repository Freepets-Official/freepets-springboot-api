package com.freepets.infra.geocoding;

import java.math.BigDecimal;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 카카오 로컬 API(주소 검색) 응답을 판정한다.
 *
 * <p>스프링에 의존하지 않는 POJO다. 운영에서는 {@code GeocodingConfig}가 빈으로 등록한다.
 */
public class KakaoGeocodingClient {

    private final KakaoGeocodingApiCaller apiCaller;
    private final String restApiKey;

    public KakaoGeocodingClient(
            KakaoGeocodingApiCaller apiCaller,
            String restApiKey
    ) {
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new IllegalArgumentException("카카오 로컬 API REST 키가 비어 있습니다.");
        }
        this.apiCaller = apiCaller;
        this.restApiKey = restApiKey;
    }

    /**
     * 주소를 좌표로 변환한다. 검색 결과가 없으면(존재하지 않는 주소, 오탈자 등) 통신은 정상이므로
     * 예외가 아니라 빈 값으로 표현한다 — {@code NtsClient.validate}가 "불일치"를 예외가 아닌 결과값으로
     * 돌려주는 것과 같은 구분이다.
     *
     * <p>카카오 응답의 {@code x}는 경도, {@code y}는 위도다 — 뒤바뀌기 쉬운 지점이라 특히 주의한다.
     *
     * @throws GeocodingException 통신 실패, 응답을 JSON으로 읽지 못한 경우
     */
    public Optional<GeocodedAddress> geocode(String address) {
        JsonNode response = apiCaller.searchAddress(address, restApiKey);
        JsonNode document = response.path("documents").path(0);

        if (document.isMissingNode() || document.isEmpty()) {
            return Optional.empty();
        }

        // x=경도, y=위도.
        BigDecimal lng = new BigDecimal(document.path("x").asText());
        BigDecimal lat = new BigDecimal(document.path("y").asText());

        return Optional.of(new GeocodedAddress(lat, lng));
    }
}
