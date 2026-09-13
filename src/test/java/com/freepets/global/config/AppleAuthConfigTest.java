package com.freepets.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.freepets.global.crypto.AppleTokenCipher;
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

        assertThat(config.appleTokenClient(new OAuthApiCaller(), config.appleTokenCipher())).isNotNull();
        assertThat(config.appleTokenCipher().isEnabled()).isTrue();
    }

    // 키 발급 전에도 서버는 떠야 한다 — 빈이 없을 뿐 예외가 나면 안 된다.
    @Test
    void 애플_설정이_통째로_없어도_예외가_나지_않는다() {
        AppleAuthConfig config = configWith(null);

        assertThat(config.appleTokenClient(new OAuthApiCaller(), config.appleTokenCipher())).isNull();
        assertThat(config.appleTokenCipher().isEnabled()).isFalse();
    }

    @Test
    void 폐기용_설정이_비어_있으면_토큰_클라이언트를_등록하지_않는다() {
        AppleAuthConfig config = configWith(
                new OAuthProperties.Apple(CLIENT_IDS, null, null, null, null, null)
        );

        assertThat(config.appleTokenClient(new OAuthApiCaller(), config.appleTokenCipher())).isNull();
    }

    @Test
    void 암호화_설정만_빠져도_토큰_클라이언트를_등록하지_않는다() {
        AppleAuthConfig config = configWith(
                new OAuthProperties.Apple(CLIENT_IDS, TEAM_ID, KEY_ID, privateKey, null, null)
        );

        assertThat(config.appleTokenClient(new OAuthApiCaller(), config.appleTokenCipher())).isNull();
    }

    // 잘못된 client_id로 폐기하면 애플이 invalid_client로 거절한다. 조용히 실패하느니 꺼두는 게 낫다.
    @Test
    void client_id가_여러_개면_토큰_클라이언트를_등록하지_않는다() {
        AppleAuthConfig config = configWith(new OAuthProperties.Apple(
                List.of("com.freepets.app", "com.freepets.web"),
                TEAM_ID, KEY_ID, privateKey, PASSWORD, SALT
        ));

        assertThat(config.appleTokenClient(new OAuthApiCaller(), config.appleTokenCipher())).isNull();
    }

    // 키 형식이 틀렸다고 서버까지 못 뜨게 하면 안 된다.
    @Test
    void 개인키_형식이_틀려도_기동을_막지_않는다() {
        AppleAuthConfig config = configWith(new OAuthProperties.Apple(
                CLIENT_IDS, TEAM_ID, KEY_ID, "이건 키가 아닙니다", PASSWORD, SALT
        ));

        assertThat(config.appleTokenClient(new OAuthApiCaller(), config.appleTokenCipher())).isNull();
    }

    // 솔트는 16진수여야 한다. 그대로 넘기면 암복호기 빈 생성이 실패해 오타 하나로 서버가 기동조차
    // 못 한다 — 개인키와 똑같이 경고만 남기고 비활성으로 넘어가야 한다.
    @Test
    void 솔트가_16진수가_아니어도_기동을_막지_않는다() {
        AppleAuthConfig config = configWith(new OAuthProperties.Apple(
                CLIENT_IDS, TEAM_ID, KEY_ID, privateKey, PASSWORD, "16진수가-아닌-솔트"
        ));

        assertThat(config.appleTokenCipher().isEnabled()).isFalse();
        assertThat(config.appleTokenClient(new OAuthApiCaller(), config.appleTokenCipher())).isNull();
    }

    // 암복호가 안 되면 토큰을 저장할 수 없다. "활성화되었습니다" 로그만 찍히고 실제로는 아무것도
    // 안 되는 상태를 만들지 않는다.
    @Test
    void 암복호기가_비활성이면_토큰_클라이언트도_등록하지_않는다() {
        AppleAuthConfig config = configWith(fullyConfigured());

        assertThat(config.appleTokenClient(new OAuthApiCaller(), AppleTokenCipher.disabled())).isNull();
    }
}
