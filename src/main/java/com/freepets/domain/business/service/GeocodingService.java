package com.freepets.domain.business.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.infra.geocoding.GeocodedAddress;
import com.freepets.infra.geocoding.GeocodingException;
import com.freepets.infra.geocoding.KakaoGeocodingClient;
import com.freepets.infra.geocoding.KakaoGeocodingProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 신규 매장 등록의 주소 → 좌표 변환. 카카오 로컬 API 응답을 판정만 하고, 실패 사유를 도메인 예외로 바꾼다.
 *
 * <p>트랜잭션을 열지 않는다. 외부 API 응답을 트랜잭션 안에서 기다리며 DB 커넥션을 붙잡을 이유가 없다
 * ({@code BusinessQueryService}와 같은 판단).
 */
@Slf4j
@Service
public class GeocodingService {

    private final KakaoGeocodingProperties kakaoGeocodingProperties;
    private final KakaoGeocodingClient kakaoGeocodingClient;

    /**
     * {@code KakaoGeocodingClient}를 지연 주입받는다. 이 빈은 REST API 키가 없으면 생성에 실패하는데,
     * 그냥 주입받으면 그 실패가 기동 시점에 터져 키가 없는 환경에서 서버가 아예 뜨지 않는다
     * ({@code BusinessQueryService}와 같은 이유).
     */
    public GeocodingService(
            KakaoGeocodingProperties kakaoGeocodingProperties,
            @Lazy KakaoGeocodingClient kakaoGeocodingClient
    ) {
        this.kakaoGeocodingProperties = kakaoGeocodingProperties;
        this.kakaoGeocodingClient = kakaoGeocodingClient;
    }

    /**
     * @throws GeneralException 키 미설정·통신 실패는 {@code BUSINESS5002}(502), 주소를 못 찾으면
     *                           {@code BUSINESS4011}(400)
     */
    public GeocodedAddress geocode(String address) {
        if (!hasApiKey()) {
            log.error("카카오 로컬 API 키가 설정되지 않았습니다. application.yml의 kakao.geocoding.rest-api-key를 확인하세요.");
            throw new GeneralException(ErrorStatus.BUSINESS5002);
        }

        try {
            return kakaoGeocodingClient.geocode(address)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.BUSINESS4011));
        } catch (GeocodingException exception) {
            log.warn("카카오 지오코딩 호출 실패: {}", exception.getMessage(), exception);
            throw new GeneralException(ErrorStatus.BUSINESS5002);
        }
    }

    private boolean hasApiKey() {
        String restApiKey = kakaoGeocodingProperties.restApiKey();
        return restApiKey != null && !restApiKey.isBlank();
    }
}
