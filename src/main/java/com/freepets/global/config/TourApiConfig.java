package com.freepets.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

import com.freepets.infra.tourapi.TourApiClient;
import com.freepets.infra.tourapi.TourApiProperties;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableConfigurationProperties(TourApiProperties.class)
@RequiredArgsConstructor
public class TourApiConfig {

    private final TourApiProperties tourApiProperties;

    /** 요청 경로에서 쓰는 클라이언트를 가리키는 빈 이름. */
    public static final String ON_DEMAND_CLIENT = "onDemandTourApiClient";

    /**
     * 적재 배치용 클라이언트. 수천 페이지를 연달아 돌므로 호출 간격을 지킨다.
     *
     * <p>지연 생성한다. 인증키가 없는 환경에서 애플리케이션 기동 자체가 막히지 않도록 하기 위해서다.
     */
    @Bean
    @Lazy
    @Primary
    public TourApiClient tourApiClient() {
        return new TourApiClient(
                tourApiProperties.serviceKey(),
                TourApiClient.BATCH_INTERVAL_MILLIS
        );
    }

    /**
     * 조회 요청에서 쓰는 클라이언트. 배치용과 인스턴스를 나눈 이유는 호출 간격 때문이다.
     *
     * <p>요청 한 건이 호출 한 번으로 끝나므로 간격을 둘 이유가 없고, 배치용을 그대로 쓰면 동시에
     * 들어온 요청들이 서로를 250ms씩 기다리게 된다.
     */
    @Bean(name = ON_DEMAND_CLIENT)
    @Lazy
    public TourApiClient onDemandTourApiClient() {
        return new TourApiClient(
                tourApiProperties.serviceKey(),
                TourApiClient.NO_INTERVAL_MILLIS
        );
    }

}
