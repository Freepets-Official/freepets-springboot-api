package com.freepets.infra.nts;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 국세청 사업자등록정보 진위확인 및 상태조회 서비스(공공데이터포털) 설정.
 *
 * <pre>
 * nts:
 *   service-key: ${NTS_SERVICE_KEY:}
 * </pre>
 *
 * <p>키는 저장소에 올리지 않는다. {@code application.yml}은 {@code .gitignore} 대상이고,
 * 값은 환경변수 {@code NTS_SERVICE_KEY}로 주입한다. 앱 번들에 넣으면 그대로 유출된다.
 */
@ConfigurationProperties(prefix = "nts")
public record NtsProperties(
        String serviceKey
) {
}
