package com.freepets.infra.tourapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 한국관광공사 국문 관광정보 서비스(KorService2) 설정.
 *
 * <pre>
 * tour-api:
 *   service-key: 발급받은키
 * </pre>
 */
@ConfigurationProperties(prefix = "tour-api")
public record TourApiProperties(
        String serviceKey
) {
}
