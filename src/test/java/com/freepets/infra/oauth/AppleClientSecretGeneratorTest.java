package com.freepets.infra.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

/**
 * 애플 실키 없이 검증한다 — 테스트에서 P-256 키쌍을 즉석에서 만들고, 생성된 JWT를 그 공개키로
 * 파싱해 애플이 요구하는 모양인지 확인한다.
 */
class AppleClientSecretGeneratorTest {

    private static final String TEAM_ID = "ABCDE12345";
    private static final String KEY_ID = "KEY1234567";
    private static final String CLIENT_ID = "com.freepets.app";
    private static final String APPLE_AUDIENCE = "https://appleid.apple.com";

    private static KeyPair keyPair;
    private static String base64PrivateKey;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = keyPairGenerator.generateKeyPair();
        base64PrivateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
    }

    @Test
    void 애플이_요구하는_클레임을_담아_ES256으로_서명한다() {
        String clientSecret = generatorWith(base64PrivateKey).generate();

        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(clientSecret);

        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("ES256");
        assertThat(parsed.getHeader().getKeyId()).isEqualTo(KEY_ID);
        assertThat(parsed.getPayload().getIssuer()).isEqualTo(TEAM_ID);
        assertThat(parsed.getPayload().getSubject()).isEqualTo(CLIENT_ID);
        assertThat(parsed.getPayload().getAudience()).containsExactly(APPLE_AUDIENCE);
        assertThat(parsed.getPayload().getExpiration()).isAfter(parsed.getPayload().getIssuedAt());
    }

    // 환경변수로 키를 넘기다 보면 개행이 어떤 형태로 들어올지 알 수 없다. 셋 다 받아야 한다.
    @Test
    void 개행이_어떤_형태로_들어와도_같은_키로_읽는다() {
        String pemWithRealNewlines = "-----BEGIN PRIVATE KEY-----\n"
                + base64PrivateKey + "\n"
                + "-----END PRIVATE KEY-----";
        String pemWithEscapedNewlines = pemWithRealNewlines.replace("\n", "\\n");

        String fromPlainBase64 = generatorWith(base64PrivateKey).generate();
        String fromRealNewlines = generatorWith(pemWithRealNewlines).generate();
        String fromEscapedNewlines = generatorWith(pemWithEscapedNewlines).generate();

        // 서명은 매번 달라질 수 있으므로(ECDSA는 확률적) 같은 공개키로 열리는지로 확인한다.
        for (String clientSecret : new String[]{fromPlainBase64, fromRealNewlines, fromEscapedNewlines}) {
            assertThat(Jwts.parser()
                    .verifyWith(keyPair.getPublic())
                    .build()
                    .parseSignedClaims(clientSecret)
                    .getPayload()
                    .getIssuer()).isEqualTo(TEAM_ID);
        }
    }

    @Test
    void 호출할_때마다_새로_만든다() throws NoSuchAlgorithmException {
        AppleClientSecretGenerator generator = generatorWith(base64PrivateKey);

        assertThat(generator.generate()).isNotBlank();
        assertThat(generator.generate()).isNotBlank();
    }

    @Test
    void 키가_비어_있으면_예외가_난다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> generatorWith("  ")
        );
    }

    @Test
    void Base64가_아닌_키는_예외가_난다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> generatorWith("이건 키가 아닙니다!!!")
        );
    }

    @Test
    void EC_개인키가_아니면_예외가_난다() {
        String notAnEcKey = Base64.getEncoder().encodeToString("not a pkcs8 key".getBytes());

        assertThrows(
                IllegalArgumentException.class,
                () -> generatorWith(notAnEcKey)
        );
    }

    // 키 원문이 로그로 새면 폐기 권한을 가진 값이 그대로 노출된다.
    @Test
    void 예외_메시지에_키_원문을_담지_않는다() {
        String secretLookingKey = Base64.getEncoder().encodeToString("super-secret-material".getBytes());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> generatorWith(secretLookingKey)
        );

        assertThat(exception.getMessage()).doesNotContain(secretLookingKey);
    }

    private AppleClientSecretGenerator generatorWith(String privateKeyText) {
        return new AppleClientSecretGenerator(TEAM_ID, KEY_ID, CLIENT_ID, privateKeyText);
    }
}
