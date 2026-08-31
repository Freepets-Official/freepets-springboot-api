package com.freepets.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.freepets.infra.anthropic.AnthropicProperties;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableConfigurationProperties(AnthropicProperties.class)
@RequiredArgsConstructor
public class AnthropicConfig {

    private final AnthropicProperties anthropicProperties;

    /**
     * 시설 조건 파싱(FacilityConditionLlmParser)에서만 쓰이므로 지연 생성한다.
     * SDK 클라이언트 빌더는 키 형식을 즉시 검증하지 않으므로(TourApiClient와 달리), 키가
     * 비어 있어도 애플리케이션 기동 자체는 막히지 않는다 — 실제 호출 시점에만 인증 오류가 난다.
     */
    @Bean
    @Lazy
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder()
                .apiKey(anthropicProperties.apiKey())
                .build();
    }

}
