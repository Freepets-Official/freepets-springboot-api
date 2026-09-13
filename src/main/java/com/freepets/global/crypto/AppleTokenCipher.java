package com.freepets.global.crypto;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * 애플 refresh token을 DB에 넣기 전에 암호화하고, 꺼내 쓸 때 복호화한다.
 *
 * <p>암복호를 직접 구현하지 않고 {@link Encryptors#delux}에 맡긴다 — AES-256-GCM에 매 건 랜덤
 * IV를 붙여주는 검증된 구현이고, {@code spring-security-crypto}가 이미 의존성에 있어 추가로
 * 들여올 것도 없다. IV 생성·결합과 인증 태그 길이는 직접 다루면 틀리기 쉬운 부분이다.
 *
 * <p><b>비밀번호·솔트가 없으면 비활성 상태로 만들어진다.</b> 애플 키 발급 전에도 서버는 떠야
 * 하기 때문이다. 이때 {@link #isEnabled()}가 {@code false}를 돌려주므로 호출하는 쪽이 기능을
 * 통째로 건너뛴다. 그래도 암복호를 호출하면 예외를 던진다 — 평문이 DB에 들어가는 경로를
 * 만들지 않기 위해서다.
 *
 * <p>키를 바꾸면 이미 저장된 값은 복호화할 수 없다. 지금은 폐기 대기 중인 토큰만 담고 있어
 * 영향이 제한적이라 키 버전 개념을 두지 않았다 — 필요해지면 암호문 앞에 버전을 붙이는 방식으로
 * 확장해야 한다.
 */
public class AppleTokenCipher {

    private final TextEncryptor textEncryptor;

    public AppleTokenCipher(
            String password,
            String salt
    ) {
        this.textEncryptor = isConfigured(password, salt)
                ? Encryptors.delux(password, salt)
                : null;
    }

    /** 설정이 갖춰져 실제로 암복호를 할 수 있는 상태인지. 애플 토큰 기능 전체의 on/off 조건 중 하나다. */
    public boolean isEnabled() {
        return textEncryptor != null;
    }

    public String encrypt(String plainText) {
        requireEnabled();
        return textEncryptor.encrypt(plainText);
    }

    public String decrypt(String encryptedText) {
        requireEnabled();
        return textEncryptor.decrypt(encryptedText);
    }

    private void requireEnabled() {
        if (textEncryptor == null) {
            throw new IllegalStateException(
                    "oauth.apple.token-encryption-password 또는 token-encryption-salt 설정이 없어 "
                            + "애플 토큰을 암복호화할 수 없습니다. 호출 전에 isEnabled()로 확인해주세요."
            );
        }
    }

    private static boolean isConfigured(
            String password,
            String salt
    ) {
        return password != null && !password.isBlank()
                && salt != null && !salt.isBlank();
    }
}
