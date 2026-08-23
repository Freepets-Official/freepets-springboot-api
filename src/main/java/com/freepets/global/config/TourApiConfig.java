package com.freepets.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.freepets.infra.tourapi.TourApiClient;
import com.freepets.infra.tourapi.TourApiProperties;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableConfigurationProperties(TourApiProperties.class)
@RequiredArgsConstructor
public class TourApiConfig {

    private final TourApiProperties tourApiProperties;

    /**
     * 적재 배치에서만 쓰이므로 지연 생성한다.
     * 인증키가 없는 환경에서 애플리케이션 기동 자체가 막히지 않도록 하기 위해서다.
     */
    @Bean
    @Lazy
    public TourApiClient tourApiClient() {
        return new TourApiClient(tourApiProperties.serviceKey());
    }

}
