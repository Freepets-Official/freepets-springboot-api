package com.freepets.domain.user.dto;

public class UserResponseDTO {

    private UserResponseDTO() {}

    public static class SignUpResult {}

    public static class PushTokenResult {}

    public static class WithdrawResult {}

    public record LoginResult(
            String accessToken,
            String refreshToken
    ) {}

    public record AccountResult(
            String nickname,
            String avatarUri
    ) {}
}
