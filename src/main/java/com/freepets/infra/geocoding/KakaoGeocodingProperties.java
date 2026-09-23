package com.freepets.infra.geocoding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 로컬 API(주소 검색) 설정.
 *
 * <pre>
 * kakao:
 *   geocoding:
 *     rest-api-key: ${KAKAO_GEOCODING_REST_API_KEY:}
 * </pre>
 *
 * <p>카카오 로그인({@code oauth.kakao.app-id})과는 별개의 키다 — 카카오 개발자 콘솔에서 로컬 API용
 * REST API 키를 따로 발급받아 쓴다. 키는 저장소에 올리지 않는다. {@code application.yml}은
 * {@code .gitignore} 대상이고, 값은 환경변수로 주입한다.
 */
@ConfigurationProperties(prefix = "kakao.geocoding")
public record KakaoGeocodingProperties(
        String restApiKey
) {
}
