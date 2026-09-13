package com.freepets.infra.oauth;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

import io.jsonwebtoken.Jwts;

/**
 * 애플 토큰 API({@code /auth/token}, {@code /auth/revoke})에 붙일 {@code client_secret}을 만든다.
 *
 * <p>애플은 고정 문자열 시크릿을 쓰지 않고, 우리가 Apple Developer 콘솔에서 받은 개인키로 직접
 * 서명한 JWT를 시크릿으로 요구한다. 그래서 값이 아니라 생성기가 필요하다.
 *
 * <p>서명은 ES256(P-256 타원곡선)이다. 우리 자체 토큰({@code JwtProvider})은 대칭키 HS256이라
 * 같은 코드를 쓸 수 없다 — 알고리즘도 키 형식도 다르다.
 *
 * <p><b>개인키는 생성자에서 한 번만 읽어 보관하고, JWT는 호출할 때마다 새로 만든다.</b> 애플은
 * 최대 6개월짜리를 허용하지만, 길게 발급해두고 캐시하면 만료를 직접 관리해야 한다. 서명 한 번은
 * 매우 싸므로 5분짜리를 그때그때 만드는 편이 단순하고 안전하다.
 */
public class AppleClientSecretGenerator {

    private static final String APPLE_AUDIENCE = "https://appleid.apple.com";
    private static final Duration EXPIRATION = Duration.ofMinutes(5);
    private static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_FOOTER = "-----END PRIVATE KEY-----";

    private final String teamId;
    private final String keyId;
    private final String clientId;
    private final PrivateKey privateKey;

    public AppleClientSecretGenerator(
            String teamId,
            String keyId,
            String clientId,
            String privateKeyText
    ) {
        this.teamId = teamId;
        this.keyId = keyId;
        this.clientId = clientId;
        this.privateKey = toPrivateKey(privateKeyText);
    }

    public String generate() {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION.toMillis());

        return Jwts.builder()
                // 어떤 키로 서명했는지 애플이 알아야 검증할 수 있다. 콘솔에서 키를 새로 만들면 바뀐다.
                .header().keyId(keyId).and()
                .issuer(teamId)
                .subject(clientId)
                .audience().add(APPLE_AUDIENCE).and()
                .issuedAt(now)
                .expiration(expiry)
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }

    /**
     * {@code .p8} 파일 내용을 개인키로 바꾼다.
     *
     * <p>PEM 헤더·푸터와 모든 공백을 털어낸 뒤 Base64로 디코딩한다. 운영은 EC2 환경변수로 키를
     * 주입하는데 환경변수에 줄바꿈을 넣기가 까다로워, 어떤 형태로 들어와도 받아들이기 위함이다 —
     * 진짜 여러 줄 PEM, 줄바꿈이 이스케이프 문자로 박제된 PEM, 헤더를 떼어낸 한 줄 Base64가 모두
     * 같은 결과가 된다.
     */
    private static PrivateKey toPrivateKey(String privateKeyText) {
        if (privateKeyText == null || privateKeyText.isBlank()) {
            throw new IllegalArgumentException("oauth.apple.private-key 설정이 비어 있습니다.");
        }

        String base64 = privateKeyText
                .replace(PEM_HEADER, "")
                .replace(PEM_FOOTER, "")
                // 이스케이프된 줄바꿈은 환경변수로 넘어오며 두 글자로 굳어버린 경우다.
                .replace("\\n", "")
                .replace("\\r", "")
                .replaceAll("\\s", "");

        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("oauth.apple.private-key를 Base64로 읽지 못했습니다.", exception);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException exception) {
            throw new IllegalArgumentException(
                    "oauth.apple.private-key가 PKCS#8 형식의 EC 개인키가 아닙니다. "
                            + "Apple Developer > Keys에서 받은 .p8 파일 내용을 그대로 넣어주세요.",
                    exception
            );
        }
    }
}
