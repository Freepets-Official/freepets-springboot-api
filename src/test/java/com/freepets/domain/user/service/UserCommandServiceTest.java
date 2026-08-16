package com.freepets.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserCommandService userCommandService;

    private UserRequestDTO.SignUpRequest createSignUpRequest() {
        UserRequestDTO.SignUpRequest request = new UserRequestDTO.SignUpRequest();
        request.setEmail("test@test.com");
        request.setPassword("password1");
        request.setNickname("tester");
        return request;
    }

    @Test
    void signUp_성공하면_저장된_유저를_반환한다() {
        UserRequestDTO.SignUpRequest request = createSignUpRequest();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO.SignUpResult result = userCommandService.signUp(request);

        assertThat(result).isNotNull();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo(request.getEmail());
        assertThat(savedUser.getNickname()).isEqualTo(request.getNickname());
        assertThat(savedUser.getPasswordHash()).isEqualTo("encodedPassword");
        assertThat(savedUser.getProvider()).isEqualTo(Provider.LOCAL);
    }

    @Test
    void signUp_이메일이_중복되면_예외를_던진다() {
        UserRequestDTO.SignUpRequest request = createSignUpRequest();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> userCommandService.signUp(request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.MEMBER4001);
        verify(userRepository, never()).save(any());
    }
}
