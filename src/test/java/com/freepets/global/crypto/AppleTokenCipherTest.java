package com.freepets.global.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

class AppleTokenCipherTest {

    private static final String PASSWORD = "test-encryption-password";
    private static final String SALT = "0123456789abcdef";
    private static final String PLAIN_TOKEN = "r.AdeadbeefAppleRefreshTokenValue";

    private final AppleTokenCipher cipher = new AppleTokenCipher(PASSWORD, SALT);

    @Test
    void 암호화한_값을_그대로_복호화한다() {
        String encrypted = cipher.encrypt(PLAIN_TOKEN);

        assertThat(cipher.decrypt(encrypted)).isEqualTo(PLAIN_TOKEN);
    }

    @Test
    void 암호문은_평문을_그대로_담지_않는다() {
        assertThat(cipher.encrypt(PLAIN_TOKEN)).doesNotContain(PLAIN_TOKEN);
    }

    // GCM에서 같은 키로 IV를 재사용하면 평문이 복구될 수 있다. 매번 다른 암호문이 나오는지가
    // IV가 실제로 새로 생성되고 있다는 증거다.
    @Test
    void 같은_값을_두_번_암호화하면_서로_다른_암호문이_나온다() {
        assertThat(cipher.encrypt(PLAIN_TOKEN)).isNotEqualTo(cipher.encrypt(PLAIN_TOKEN));
    }

    @Test
    void 변조된_암호문은_복호화하지_못한다() {
        String encrypted = cipher.encrypt(PLAIN_TOKEN);
        String tampered = encrypted.substring(0, encrypted.length() - 2)
                + (encrypted.endsWith("aa") ? "bb" : "aa");

        assertThrows(RuntimeException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void 다른_키로는_복호화하지_못한다() {
        String encrypted = cipher.encrypt(PLAIN_TOKEN);
        AppleTokenCipher otherCipher = new AppleTokenCipher("another-password", SALT);

        assertThrows(RuntimeException.class, () -> otherCipher.decrypt(encrypted));
    }

    @Test
    void 설정이_있으면_활성_상태다() {
        assertThat(cipher.isEnabled()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @CsvSource({"''", "'   '"})
    void 비밀번호가_없으면_비활성_상태다(String password) {
        assertThat(new AppleTokenCipher(password, SALT).isEnabled()).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @CsvSource({"''", "'   '"})
    void 솔트가_없으면_비활성_상태다(String salt) {
        assertThat(new AppleTokenCipher(PASSWORD, salt).isEnabled()).isFalse();
    }

    // 비활성 상태에서 조용히 평문을 돌려주면 그 값이 그대로 DB에 들어간다. 반드시 터져야 한다.
    @Test
    void 비활성_상태에서_암복호를_호출하면_예외가_난다() {
        AppleTokenCipher disabled = new AppleTokenCipher(null, null);

        assertThrows(IllegalStateException.class, () -> disabled.encrypt(PLAIN_TOKEN));
        assertThrows(IllegalStateException.class, () -> disabled.decrypt("anything"));
    }
}
