package com.freepets.domain.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.user.converter.UserConverter;
import com.freepets.domain.user.dto.UserRequestDTO;
import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.global.security.jwt.JwtProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public UserResponseDTO.LoginResult login(UserRequestDTO.LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));

        // 소셜 가입자는 passwordHash가 null이다. matches()에 null을 넘기면 구현체에 따라
        // NPE가 나므로, 비밀번호 로그인 대상이 아님을 먼저 걸러낸다.
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new GeneralException(ErrorStatus.MEMBER4006);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        return UserConverter.toLoginResult(accessToken, refreshToken);
    }

    public UserResponseDTO.AccountResult getAccount(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));

        return UserConverter.toAccountResult(user);
    }
}
