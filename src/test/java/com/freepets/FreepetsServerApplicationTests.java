package com.freepets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 빈 배선이 성립하는지만 확인한다.
 *
 * <p>{@code oauth.*}에 더미 값을 넣는 이유 — {@code OAuthConfig}는 구글·애플 클라이언트 ID가
 * 없으면 기동을 막는다(설정 누락을 첫 로그인 요청까지 미루지 않으려는 의도적 선택이다).
 * 그런데 {@code application.yml}은 시크릿 때문에 gitignore돼 있고 {@code src/test/resources}도
 * 없어서, 실제 값에 의존하면 새로 클론한 사람마다 이 테스트가 깨진다. 여기서 검증하려는 것은
 * "배선이 맞는가"이므로 값의 진위는 무관하다. 설정 누락은 {@code bootRun} 시점에 드러난다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "oauth.google.client-ids=dummy-google-client-id",
        "oauth.apple.client-ids=dummy-apple-bundle-id",
        "oauth.kakao.app-id=0"
})
class FreepetsServerApplicationTests {

    @Test
    void contextLoads() {
    }

}
