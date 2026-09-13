package com.freepets.global.config;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.freepets.global.crypto.AppleTokenCipher;
import com.freepets.infra.oauth.AppleClientSecretGenerator;
import com.freepets.infra.oauth.AppleTokenClient;
import com.freepets.infra.oauth.OAuthApiCaller;
import com.freepets.infra.oauth.OAuthProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 애플 토큰 폐기(App Store 심사 지침 5.1.1(v)) 관련 빈 등록.
 *
 * <p><b>{@code OAuthConfig}와 정책이 정반대라 파일을 나눴다.</b> 그쪽은 설정이 없으면 기동을
 * 막는다 — 소셜 로그인은 핵심 기능이고 설정이 빠진 채 뜨면 첫 로그인에서 500을 만나기 때문이다.
 * 반면 여기 설정은 <b>없어도 기동해야 한다</b>. 애플 콘솔에서 키를 받기 전에도 서버는 떠야 하고,
 * 로그인·탈퇴는 이 값들 없이도 온전히 동작한다. 두 정책을 한 클래스에 섞으면 그쪽 Javadoc이
 * 거짓말이 되므로 경계를 파일로 드러낸다.
 *
 * <p>설정이 빠지면 폐기 기능만 조용히 꺼진다. 다만 그 상태로 심사에 내면 로그인·탈퇴가 멀쩡히
 * 동작하면서 토큰만 폐기되지 않아 리젝되므로, 기동 로그에 경고를 남겨 눈에 띄게 한다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AppleAuthConfig {

    private final OAuthProperties oAuthProperties;

    /**
     * 애플 토큰 암복호기. <b>설정이 없어도 빈은 항상 등록한다</b> — 주입받는 쪽이 빈 존재 여부가
     * 아니라 {@link AppleTokenCipher#isEnabled()}로 판단하게 해서, 설정 유무가 컨텍스트 구성을
     * 흔들지 않게 한다.
     */
    @Bean
    public AppleTokenCipher appleTokenCipher() {
        OAuthProperties.Apple apple = oAuthProperties.apple();

        return new AppleTokenCipher(
                apple == null ? null : apple.tokenEncryptionPassword(),
                apple == null ? null : apple.tokenEncryptionSalt()
        );
    }

    /**
     * 애플 토큰 교환·폐기 클라이언트.
     *
     * <p>설정이 하나라도 빠지면 {@code null}을 돌려준다. 주입받는 쪽은 {@code ObjectProvider}로
     * 받아 없는 상태를 정상으로 처리한다 — 쓸 수 없는 클라이언트를 만들어두고 호출 시점에
     * 터뜨리는 것보다, 아예 없다는 사실이 드러나는 편이 낫다.
     */
    @Bean
    public AppleTokenClient appleTokenClient(OAuthApiCaller oAuthApiCaller) {
        OAuthProperties.Apple apple = oAuthProperties.apple();
        if (apple == null) {
            warnDisabled("oauth.apple 설정이 통째로 없습니다.");
            return null;
        }

        List<String> missingKeys = missingKeysOf(apple);
        if (!missingKeys.isEmpty()) {
            warnDisabled("다음 설정이 비어 있습니다: " + String.join(", ", missingKeys));
            return null;
        }

        String clientId = resolveTokenClientId(apple.clientIds());
        if (clientId == null) {
            warnDisabled("oauth.apple.client-ids에 값이 여러 개라 토큰 교환에 쓸 client_id를 정할 수 없습니다.");
            return null;
        }

        try {
            AppleClientSecretGenerator clientSecretGenerator = new AppleClientSecretGenerator(
                    apple.teamId(),
                    apple.keyId(),
                    clientId,
                    apple.privateKey()
            );
            log.info("애플 토큰 폐기가 활성화되었습니다. clientId={}", clientId);
            return new AppleTokenClient(oAuthApiCaller, clientSecretGenerator, clientId);
        } catch (IllegalArgumentException exception) {
            // 키 형식이 틀렸다고 서버를 못 뜨게 하지는 않는다. 다만 원인은 분명히 남긴다 —
            // 예외 메시지에 키 원문은 들어가지 않는다(AppleClientSecretGenerator 참고).
            warnDisabled("oauth.apple.private-key를 읽지 못했습니다: " + exception.getMessage());
            return null;
        }
    }

    /**
     * 교환·폐기에 쓸 {@code client_id}를 고른다.
     *
     * <p>폐기 요청의 {@code client_id}는 그 토큰을 발급받을 때 쓴 값과 같아야 한다. 지금은
     * iOS 앱 하나뿐이라 {@code client-ids}에 값이 하나면 그걸 쓰면 된다. 웹 Service ID가
     * 추가되면 사용자마다 값이 달라지므로 그때는 어느 것으로 발급받았는지 함께 저장해야 한다 —
     * 그 전까지는 잘못된 값으로 폐기를 시도해 조용히 실패하는 일이 없도록 아예 끈다.
     */
    private String resolveTokenClientId(List<String> clientIds) {
        return clientIds != null && clientIds.size() == 1 ? clientIds.get(0).trim() : null;
    }

    private List<String> missingKeysOf(OAuthProperties.Apple apple) {
        return Stream.of(
                        isBlank(apple.teamId()) ? "oauth.apple.team-id" : null,
                        isBlank(apple.keyId()) ? "oauth.apple.key-id" : null,
                        isBlank(apple.privateKey()) ? "oauth.apple.private-key" : null,
                        isBlank(apple.tokenEncryptionPassword()) ? "oauth.apple.token-encryption-password" : null,
                        isBlank(apple.tokenEncryptionSalt()) ? "oauth.apple.token-encryption-salt" : null
                )
                .filter(Objects::nonNull)
                .toList();
    }

    private void warnDisabled(String reason) {
        log.warn("애플 토큰 폐기가 비활성 상태입니다 — {} 이대로 심사에 제출하면 계정 삭제 시 애플 토큰이 "
                + "폐기되지 않아 심사 지침 5.1.1(v)로 리젝될 수 있습니다.", reason);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
