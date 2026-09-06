package com.freepets.domain.user.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

/**
 * 소셜 가입 시 값 보정 규칙 검증.
 *
 * <p>{@code User.email}은 NOT NULL + UNIQUE, {@code nickname}은 NOT NULL(50자)인데
 * 소셜에서는 둘 다 안 올 수 있다. 그 빈틈을 어떻게 메우는지가 여기 고정된다.
 */
class UserConverterSocialTest {

    @Test
    void 소셜_가입자는_비밀번호가_없다() {
        User user = UserConverter.toSocialUser(Provider.KAKAO, "1234567890", "foo@bar.com", "홍길동");

        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getProvider()).isEqualTo(Provider.KAKAO);
        assertThat(user.getProviderId()).isEqualTo("1234567890");
        assertThat(user.getEmail()).isEqualTo("foo@bar.com");
        assertThat(user.getNickname()).isEqualTo("홍길동");
    }

    @Test
    void 이메일이_없으면_providerId_기반_플레이스홀더를_넣는다() {
        User user = UserConverter.toSocialUser(Provider.APPLE, "001234.abcd", null, "홍길동");

        // providerId 기반이라 유일하고 UNIQUE 제약을 깨지 않는다.
        assertThat(user.getEmail()).isEqualTo("apple_001234.abcd@social.freepets.local");
    }

    @Test
    void 같은_제공자의_다른_사용자는_플레이스홀더_이메일도_다르다() {
        String first = UserConverter.toSocialUser(Provider.APPLE, "sub-a", null, null).getEmail();
        String second = UserConverter.toSocialUser(Provider.APPLE, "sub-b", null, null).getEmail();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void 이름이_없으면_회원과_providerId_뒤_6자로_채운다() {
        User user = UserConverter.toSocialUser(Provider.APPLE, "001234.abcdef", null, null);

        assertThat(user.getNickname()).isEqualTo("회원abcdef");
    }

    @Test
    void 이름이_공백만_있으면_없는_것으로_본다() {
        User user = UserConverter.toSocialUser(Provider.NAVER, "abc123", null, "   ");

        assertThat(user.getNickname()).isEqualTo("회원abc123");
    }

    @Test
    void providerId가_6자보다_짧으면_그대로_쓴다() {
        User user = UserConverter.toSocialUser(Provider.KAKAO, "123", null, null);

        assertThat(user.getNickname()).isEqualTo("회원123");
    }

    @Test
    void 이름이_컬럼_길이를_넘으면_50자로_자른다() {
        String longName = "가".repeat(80);

        User user = UserConverter.toSocialUser(Provider.GOOGLE, "google-1", "foo@bar.com", longName);

        assertThat(user.getNickname()).hasSize(50);
    }
}
