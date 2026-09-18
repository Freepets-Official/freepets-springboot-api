package com.freepets.infra.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class KakaoGeocodingClientTest {

    private static final String ADDRESS = "강원 강릉시 창해로 17";
    private static final String REST_API_KEY = "test-rest-api-key";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private KakaoGeocodingApiCaller apiCaller;

    private KakaoGeocodingClient kakaoGeocodingClient;

    @BeforeEach
    void setUp() {
        kakaoGeocodingClient = new KakaoGeocodingClient(apiCaller, REST_API_KEY);
    }

    private JsonNode readTree(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    @Test
    void 정상_응답이면_x는_경도_y는_위도로_파싱한다() throws Exception {
        // 카카오 응답은 x=경도, y=위도다 — 뒤바뀌기 쉬운 지점이라 이 케이스로 회귀를 막는다.
        JsonNode response = readTree("""
                {"documents":[{"x":"128.8999999","y":"37.7999999"}]}
                """);
        when(apiCaller.searchAddress(ADDRESS, REST_API_KEY)).thenReturn(response);

        Optional<GeocodedAddress> result = kakaoGeocodingClient.geocode(ADDRESS);

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualByComparingTo(new BigDecimal("37.7999999"));
        assertThat(result.get().lng()).isEqualByComparingTo(new BigDecimal("128.8999999"));
    }

    @Test
    void 검색_결과가_없으면_빈_값을_반환한다() throws Exception {
        JsonNode response = readTree("""
                {"documents":[]}
                """);
        when(apiCaller.searchAddress(ADDRESS, REST_API_KEY)).thenReturn(response);

        Optional<GeocodedAddress> result = kakaoGeocodingClient.geocode(ADDRESS);

        assertThat(result).isEmpty();
    }

    @Test
    void documents_필드_자체가_없으면_빈_값을_반환한다() throws Exception {
        JsonNode response = readTree("""
                {}
                """);
        when(apiCaller.searchAddress(ADDRESS, REST_API_KEY)).thenReturn(response);

        Optional<GeocodedAddress> result = kakaoGeocodingClient.geocode(ADDRESS);

        assertThat(result).isEmpty();
    }
}
