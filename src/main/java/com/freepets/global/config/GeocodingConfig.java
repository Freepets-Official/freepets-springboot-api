package com.freepets.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.freepets.infra.geocoding.KakaoGeocodingApiCaller;
import com.freepets.infra.geocoding.KakaoGeocodingClient;
import com.freepets.infra.geocoding.KakaoGeocodingProperties;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableConfigurationProperties(KakaoGeocodingProperties.class)
@RequiredArgsConstructor
public class GeocodingConfig {

    private final KakaoGeocodingProperties kakaoGeocodingProperties;

    @Bean
    public KakaoGeocodingApiCaller kakaoGeocodingApiCaller() {
        return new KakaoGeocodingApiCaller();
    }

    /**
     * 키가 없는 환경에서도 기동은 되도록 지연 생성한다({@code NtsConfig}와 같은 판단).
     *
     * <p>키가 없으면 이 빈을 만들다 실패하는데, 그 예외는 스프링이 {@code BeanCreationException}으로
     * 감싸서 던지므로 서비스의 {@code GeocodingException} 처리에 걸리지 않는다. 그래서
     * {@code GeocodingService}가 호출 전에 키가 있는지 먼저 확인해 502로 내린다.
     */
    @Bean
    @Lazy
    public KakaoGeocodingClient kakaoGeocodingClient(KakaoGeocodingApiCaller kakaoGeocodingApiCaller) {
        return new KakaoGeocodingClient(kakaoGeocodingApiCaller, kakaoGeocodingProperties.restApiKey());
    }

}
