package com.freepets.domain.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.infra.geocoding.GeocodedAddress;
import com.freepets.infra.geocoding.GeocodingException;
import com.freepets.infra.geocoding.KakaoGeocodingClient;
import com.freepets.infra.geocoding.KakaoGeocodingProperties;

@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    private static final String ADDRESS = "강원 강릉시 창해로 17";

    @Mock
    private KakaoGeocodingClient kakaoGeocodingClient;

    @Test
    void 정상_주소면_좌표를_그대로_반환한다() {
        GeocodingService geocodingService = new GeocodingService(new KakaoGeocodingProperties("rest-api-key"), kakaoGeocodingClient);
        GeocodedAddress geocoded = new GeocodedAddress(new BigDecimal("37.8"), new BigDecimal("128.9"));
        when(kakaoGeocodingClient.geocode(ADDRESS)).thenReturn(Optional.of(geocoded));

        GeocodedAddress result = geocodingService.geocode(ADDRESS);

        assertThat(result).isEqualTo(geocoded);
    }

    @Test
    void 검색_결과가_없으면_BUSINESS4011() {
        GeocodingService geocodingService = new GeocodingService(new KakaoGeocodingProperties("rest-api-key"), kakaoGeocodingClient);
        when(kakaoGeocodingClient.geocode(ADDRESS)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> geocodingService.geocode(ADDRESS)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS4011);
    }

    @Test
    void 통신에_실패하면_BUSINESS5002() {
        GeocodingService geocodingService = new GeocodingService(new KakaoGeocodingProperties("rest-api-key"), kakaoGeocodingClient);
        when(kakaoGeocodingClient.geocode(ADDRESS)).thenThrow(new GeocodingException("카카오 로컬 API 호출에 실패했습니다."));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> geocodingService.geocode(ADDRESS)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS5002);
    }

    @Test
    void 키가_없으면_호출하지_않고_BUSINESS5002() {
        GeocodingService serviceWithoutKey = new GeocodingService(new KakaoGeocodingProperties("  "), kakaoGeocodingClient);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> serviceWithoutKey.geocode(ADDRESS)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.BUSINESS5002);
        verifyNoInteractions(kakaoGeocodingClient);
    }
}
