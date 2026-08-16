package com.freepets.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.freepets.domain.user.dto.UserRequestDTO;
import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.global.security.jwt.JwtProvider;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private UserQueryService userQueryService;

    private UserRequestDTO.LoginRequest createLoginRequest() {
        UserRequestDTO.LoginRequest request = new UserRequestDTO.LoginRequest();
        request.setEmail("test@test.com");
        request.setPassword("password1");
        return request;
    }

    private User createUser() {
        return User.builder()
                .email("test@test.com")
                .passwordHash("encodedPassword")
                .nickname("tester")
                .provider(Provider.LOCAL)
                .build();
    }

    @Test
    void login_성공하면_토큰을_반환한다() {
        UserRequestDTO.LoginRequest request = createLoginRequest();
        User user = createUser();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).thenReturn(true);
        when(jwtProvider.createAccessToken(user.getId())).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(user.getId())).thenReturn("refresh-token");

        UserResponseDTO.LoginResult result = userQueryService.login(request);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void login_존재하지_않는_이메일이면_예외를_던진다() {
        UserRequestDTO.LoginRequest request = createLoginRequest();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> userQueryService.login(request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.MEMBER4005);
        verifyNoInteractions(passwordEncoder, jwtProvider);
    }

    @Test
    void login_비밀번호가_일치하지_않으면_예외를_던진다() {
        UserRequestDTO.LoginRequest request = createLoginRequest();
        User user = createUser();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).thenReturn(false);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> userQueryService.login(request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.MEMBER4006);
        verify(jwtProvider, never()).createAccessToken(user.getId());
        verify(jwtProvider, never()).createRefreshToken(user.getId());
    }
}
