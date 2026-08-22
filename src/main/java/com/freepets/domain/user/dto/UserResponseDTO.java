package com.freepets.domain.user.dto;

public class UserResponseDTO {

    private UserResponseDTO() {}

    public static class SignUpResult {}

    public record LoginResult(
            String accessToken,
            String refreshToken
    ) {}

    public record AccountResult(
            String nickname,
            String avatarUri
    ) {}
}
