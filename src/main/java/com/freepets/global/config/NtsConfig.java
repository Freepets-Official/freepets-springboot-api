package com.freepets.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.freepets.infra.nts.NtsApiCaller;
import com.freepets.infra.nts.NtsClient;
import com.freepets.infra.nts.NtsProperties;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableConfigurationProperties(NtsProperties.class)
@RequiredArgsConstructor
public class NtsConfig {

    private final NtsProperties ntsProperties;

    @Bean
    public NtsApiCaller ntsApiCaller() {
        return new NtsApiCaller();
    }

    /**
     * 서비스키가 없는 환경에서도 기동은 되도록 지연 생성한다({@code TourApiConfig}와 같은 판단).
     * 공공데이터포털 키는 신청 후 승인까지 시간이 걸려서, 키를 기다리는 동안 다른 개발이 막히면 안 된다.
     *
     * <p>키가 없으면 이 빈을 만들다 실패하는데, 그 예외는 스프링이 {@code BeanCreationException}으로
     * 감싸서 던지므로 서비스의 {@code NtsException} 처리에 걸리지 않는다. 그래서
     * {@code BusinessQueryService}가 호출 전에 키가 있는지 먼저 확인해 502로 내린다 —
     * 서버 설정 문제를 사용자에게 500으로 보내지 않기 위해서다.
     *
     * <p>소셜 로그인({@code OAuthConfig})은 반대로 키가 없으면 기동을 막는다. 그쪽은 키가 없으면
     * 로그인 자체가 불가능해 배포 전에 알아야 하기 때문이다.
     */
    @Bean
    @Lazy
    public NtsClient ntsClient(NtsApiCaller ntsApiCaller) {
        return new NtsClient(ntsApiCaller, ntsProperties.serviceKey());
    }

}
