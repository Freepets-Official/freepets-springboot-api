package com.freepets.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.freepets.infra.oauth.OAuthApiCaller;
import com.freepets.infra.oauth.OAuthProperties;

/**
 * 애플 설정이 없어도 기동해야 한다는 요구사항을 배선 수준에서 고정한다. 컨텍스트 전체가 뜨는지는
 * {@code FreepetsServerApplicationTests}가 (애플 신규 키 없이) 이미 확인한다.
 */
class AppleAuthConfigTest {

    private static final List<String> CLIENT_IDS = List.of("com.freepets.app");
    private static final String TEAM_ID = "ABCDE12345";
    private static final String KEY_ID = "KEY1234567";
    private static final String PASSWORD = "encryption-password";
    private static final String SALT = "0123456789abcdef";

    private static String privateKey;

    @BeforeAll
    static void generatePrivateKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        privateKey = Base64.getEncoder().encodeToString(
                keyPairGenerator.generateKeyPair().getPrivate().getEncoded()
        );
    }

    private AppleAuthConfig configWith(OAuthProperties.Apple apple) {
        return new AppleAuthConfig(new OAuthProperties(null, apple, null));
    }

    private OAuthProperties.Apple fullyConfigured() {
        return new OAuthProperties.Apple(CLIENT_IDS, TEAM_ID, KEY_ID, privateKey, PASSWORD, SALT);
    }

    @Test
    void 설정이_모두_있으면_토큰_클라이언트를_등록한다() {
        AppleAuthConfig config = configWith(fullyConfigured());

        assertThat(config.appleTokenClient(new OAuthApiCaller())).isNotNull();
        assertThat(config.appleTokenCipher().isEnabled()).isTrue();
    }

    // 키 발급 전에도 서버는 떠야 한다 — 빈이 없을 뿐 예외가 나면 안 된다.
    @Test
    void 애플_설정이_통째로_없어도_예외가_나지_않는다() {
        AppleAuthConfig config = configWith(null);

        assertThat(config.appleTokenClient(new OAuthApiCaller())).isNull();
        assertThat(config.appleTokenCipher().isEnabled()).isFalse();
    }

    @Test
    void 폐기용_설정이_비어_있으면_토큰_클라이언트를_등록하지_않는다() {
        AppleAuthConfig config = configWith(
                new OAuthProperties.Apple(CLIENT_IDS, null, null, null, null, null)
        );

        assertThat(config.appleTokenClient(new OAuthApiCaller())).isNull();
    }

    @Test
    void 암호화_설정만_빠져도_토큰_클라이언트를_등록하지_않는다() {
        AppleAuthConfig config = configWith(
                new OAuthProperties.Apple(CLIENT_IDS, TEAM_ID, KEY_ID, privateKey, null, null)
        );

        assertThat(config.appleTokenClient(new OAuthApiCaller())).isNull();
    }

    // 잘못된 client_id로 폐기하면 애플이 invalid_client로 거절한다. 조용히 실패하느니 꺼두는 게 낫다.
    @Test
    void client_id가_여러_개면_토큰_클라이언트를_등록하지_않는다() {
        AppleAuthConfig config = configWith(new OAuthProperties.Apple(
                List.of("com.freepets.app", "com.freepets.web"),
                TEAM_ID, KEY_ID, privateKey, PASSWORD, SALT
        ));

        assertThat(config.appleTokenClient(new OAuthApiCaller())).isNull();
    }

    // 키 형식이 틀렸다고 서버까지 못 뜨게 하면 안 된다.
    @Test
    void 개인키_형식이_틀려도_기동을_막지_않는다() {
        AppleAuthConfig config = configWith(new OAuthProperties.Apple(
                CLIENT_IDS, TEAM_ID, KEY_ID, "이건 키가 아닙니다", PASSWORD, SALT
        ));

        assertThat(config.appleTokenClient(new OAuthApiCaller())).isNull();
    }
}
