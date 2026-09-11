package com.freepets.domain.user.converter;

import com.freepets.domain.user.dto.UserRequestDTO;
import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

public class UserConverter {

    /** {@code User.nickname} 컬럼 길이. 소셜 이름이 이보다 길면 잘라야 한다. */
    private static final int NICKNAME_MAX_LENGTH = 50;

    private UserConverter() {}

    public static User toUser(
            UserRequestDTO.SignUpRequest request,
            String encodedPassword
    ) {
        return User.builder()
                .email(request.getEmail())
                .passwordHash(encodedPassword)
                .nickname(request.getNickname())
                .provider(Provider.LOCAL)
                .build();
    }

    /**
     * 소셜 로그인으로 처음 들어온 사용자를 엔티티로 만든다.
     *
     * <p>파라미터를 {@code OAuthUserInfo}가 아니라 원시값으로 받는다. 도메인이 infra 계층의
     * 타입에 묶이면 소셜 응답 구조가 바뀔 때 유저 도메인까지 흔들리기 때문이다.
     *
     * <p><b>값 보정</b> — {@code User.email}은 NOT NULL + UNIQUE이고 {@code nickname}도
     * NOT NULL(50자)인데, 소셜에서는 둘 다 안 올 수 있어 여기서 채운다.
     * <ul>
     *   <li>이메일 없음(애플 비공개 릴레이 미동의, 카카오·네이버 이메일 미동의) →
     *       {@code {provider}_{providerId}@social.freepets.local}. providerId 기반이라
     *       유일하고 UNIQUE 제약을 깨지 않는다. 실제 이메일 입력 유도는 별도 이슈다</li>
     *   <li>이름 없음(애플 2회차 이후 등) → {@code 회원} + providerId 뒤 6자</li>
     *   <li>이름이 50자 초과 → 50자로 자른다</li>
     * </ul>
     */
    public static User toSocialUser(
            Provider provider,
            String providerId,
            String email,
            String name
    ) {
        return User.builder()
                .email(email != null ? email : placeholderEmail(provider, providerId))
                .passwordHash(null)
                .nickname(normalizeNickname(name, providerId))
                .provider(provider)
                .providerId(providerId)
                .build();
    }

    private static String placeholderEmail(
            Provider provider,
            String providerId
    ) {
        return provider.name().toLowerCase() + "_" + providerId + "@social.freepets.local";
    }

    private static String normalizeNickname(
            String name,
            String providerId
    ) {
        if (name == null || name.isBlank()) {
            return "회원" + lastSixCharacters(providerId);
        }

        String trimmed = name.trim();
        return trimmed.length() > NICKNAME_MAX_LENGTH
                ? trimmed.substring(0, NICKNAME_MAX_LENGTH)
                : trimmed;
    }

    private static String lastSixCharacters(String providerId) {
        return providerId.length() <= 6
                ? providerId
                : providerId.substring(providerId.length() - 6);
    }

    public static UserResponseDTO.SignUpResult toSignUpResult(User user) {
        return new UserResponseDTO.SignUpResult();
    }

    public static UserResponseDTO.LoginResult toLoginResult(
            String accessToken,
            String refreshToken
    ) {
        return new UserResponseDTO.LoginResult(accessToken, refreshToken);
    }

    public static UserResponseDTO.AccountResult toAccountResult(User user) {
        return new UserResponseDTO.AccountResult(user.getNickname(), user.getAvatarUri());
    }

    public static UserResponseDTO.PushTokenResult toPushTokenResult() {
        return new UserResponseDTO.PushTokenResult();
    }

    public static UserResponseDTO.WithdrawResult toWithdrawResult() {
        return new UserResponseDTO.WithdrawResult();
    }
}
