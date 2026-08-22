package com.freepets.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.freepets.domain.user.dto.UserRequestDTO;
import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.infra.s3.S3ImageService;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private S3ImageService s3ImageService;

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

    private User createUser() {
        return User.builder()
                .email("test@test.com")
                .passwordHash("encodedPassword")
                .nickname("tester")
                .provider(Provider.LOCAL)
                .build();
    }

    @Test
    void updateAccount_사진_변경_없이_닉네임만_수정하면_기존_아바타를_유지하고_S3를_호출하지_않는다() {
        User user = createUser();
        user.update(user.getNickname(), "https://s3-url/old.jpg");

        UserRequestDTO.UpdateAccountRequest request = new UserRequestDTO.UpdateAccountRequest();
        request.setNickname("newNickname");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponseDTO.AccountResult result = userCommandService.updateAccount(1L, request);

        assertThat(result.nickname()).isEqualTo("newNickname");
        assertThat(result.avatarUri()).isEqualTo("https://s3-url/old.jpg");
        verifyNoInteractions(s3ImageService);
    }

    @Test
    void updateAccount_새_사진을_올리면_S3에_업로드하고_기존_이미지를_삭제한다() {
        User user = createUser();
        user.update(user.getNickname(), "https://s3-url/old.jpg");

        MockMultipartFile avatar = new MockMultipartFile("avatar", "avatar.jpg", "image/jpeg", "content".getBytes());
        UserRequestDTO.UpdateAccountRequest request = new UserRequestDTO.UpdateAccountRequest();
        request.setNickname("newNickname");
        request.setAvatar(avatar);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(s3ImageService.upload(avatar)).thenReturn("https://s3-url/new.jpg");

        UserResponseDTO.AccountResult result = userCommandService.updateAccount(1L, request);

        assertThat(result.avatarUri()).isEqualTo("https://s3-url/new.jpg");
        verify(s3ImageService).delete("https://s3-url/old.jpg");
    }

    @Test
    void updateAccount_기존_이미지가_없으면_삭제를_호출하지_않는다() {
        User user = createUser();

        MockMultipartFile avatar = new MockMultipartFile("avatar", "avatar.jpg", "image/jpeg", "content".getBytes());
        UserRequestDTO.UpdateAccountRequest request = new UserRequestDTO.UpdateAccountRequest();
        request.setNickname("newNickname");
        request.setAvatar(avatar);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(s3ImageService.upload(avatar)).thenReturn("https://s3-url/new.jpg");

        userCommandService.updateAccount(1L, request);

        verify(s3ImageService, never()).delete(any());
    }

    @Test
    void updateAccount_존재하지_않는_유저면_예외를_던진다() {
        UserRequestDTO.UpdateAccountRequest request = new UserRequestDTO.UpdateAccountRequest();
        request.setNickname("newNickname");

        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> userCommandService.updateAccount(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.MEMBER4005);
        verifyNoInteractions(s3ImageService);
    }
}
