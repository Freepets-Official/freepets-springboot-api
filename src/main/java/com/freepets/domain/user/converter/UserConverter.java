package com.freepets.domain.user.converter;

import com.freepets.domain.user.dto.UserRequestDTO;
import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

public class UserConverter {

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

    public static UserResponseDTO.SignUpResult toSignUpResult(User user) {
        return new UserResponseDTO.SignUpResult();
    }

    public static UserResponseDTO.LoginResult toLoginResult(
            String accessToken,
            String refreshToken
    ) {
        return new UserResponseDTO.LoginResult(accessToken, refreshToken);
    }
}
