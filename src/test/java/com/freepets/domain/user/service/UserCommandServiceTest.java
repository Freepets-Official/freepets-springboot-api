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
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.user.dto.UserRequestDTO;
import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.entity.UserDeviceToken;
import com.freepets.domain.user.repository.UserDeviceTokenRepository;
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

    @Mock
    private UserDeviceTokenRepository userDeviceTokenRepository;

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

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

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

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
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

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(s3ImageService.upload(avatar)).thenReturn("https://s3-url/new.jpg");

        userCommandService.updateAccount(1L, request);

        verify(s3ImageService, never()).delete(any());
    }

    @Test
    void updateAccount_존재하지_않는_유저면_예외를_던진다() {
        UserRequestDTO.UpdateAccountRequest request = new UserRequestDTO.UpdateAccountRequest();
        request.setNickname("newNickname");

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> userCommandService.updateAccount(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.MEMBER4005);
        verifyNoInteractions(s3ImageService);
    }

    @Test
    void registerPushToken_기존에_같은_토큰이_있어도_지우고_새로_저장한다() {
        User user = createUser();
        UserRequestDTO.RegisterPushTokenRequest request = new UserRequestDTO.RegisterPushTokenRequest();
        request.setToken("expo-token-1");
        request.setPlatform("ANDROID");

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        userCommandService.registerPushToken(1L, request);

        verify(userDeviceTokenRepository).deleteByToken("expo-token-1");

        ArgumentCaptor<UserDeviceToken> tokenCaptor = ArgumentCaptor.forClass(UserDeviceToken.class);
        verify(userDeviceTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getToken()).isEqualTo("expo-token-1");
        assertThat(tokenCaptor.getValue().getPlatform()).isEqualTo("ANDROID");
        assertThat(tokenCaptor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    void registerPushToken_존재하지_않는_유저면_예외를_던진다() {
        UserRequestDTO.RegisterPushTokenRequest request = new UserRequestDTO.RegisterPushTokenRequest();
        request.setToken("expo-token-1");

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> userCommandService.registerPushToken(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.MEMBER4005);
        verifyNoInteractions(userDeviceTokenRepository);
    }

    @Test
    void unregisterPushToken_본인_토큰이면_삭제한다() {
        User user = createUser();
        ReflectionTestUtils.setField(user, "id", 1L);
        UserDeviceToken deviceToken = UserDeviceToken.builder()
                .user(user)
                .token("expo-token-1")
                .build();

        when(userDeviceTokenRepository.findByToken("expo-token-1")).thenReturn(Optional.of(deviceToken));

        userCommandService.unregisterPushToken(1L, "expo-token-1");

        verify(userDeviceTokenRepository).delete(deviceToken);
    }

    @Test
    void unregisterPushToken_다른_유저_토큰이면_삭제하지_않는다() {
        User owner = createUser();
        ReflectionTestUtils.setField(owner, "id", 2L);
        UserDeviceToken deviceToken = UserDeviceToken.builder()
                .user(owner)
                .token("expo-token-1")
                .build();

        when(userDeviceTokenRepository.findByToken("expo-token-1")).thenReturn(Optional.of(deviceToken));

        userCommandService.unregisterPushToken(1L, "expo-token-1");

        verify(userDeviceTokenRepository, never()).delete(any());
    }

    @Test
    void unregisterPushToken_존재하지_않는_토큰이면_조용히_넘어간다() {
        when(userDeviceTokenRepository.findByToken("expo-token-1")).thenReturn(Optional.empty());

        assertThat(userCommandService.unregisterPushToken(1L, "expo-token-1")).isNotNull();
        verify(userDeviceTokenRepository, never()).delete(any());
    }

    @Test
    void withdraw_LOCAL_계정은_비밀번호가_맞으면_탈퇴된다() {
        User user = createUser();
        user.update(user.getNickname(), "https://s3-url/avatar.jpg");
        UserRequestDTO.WithdrawRequest request = new UserRequestDTO.WithdrawRequest();
        request.setPassword("rawPassword");

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("rawPassword", "encodedPassword")).thenReturn(true);

        UserResponseDTO.WithdrawResult result = userCommandService.withdraw(1L, request);

        assertThat(result).isNotNull();
        assertThat(user.isDeleted()).isTrue();
        // 재가입을 막지 않으려면 인증에 쓰이는 값이 비워져야 한다.
        assertThat(user.getEmail()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getAvatarUri()).isNull();
        // 이미 남에게 보이는 콘텐츠(리뷰 등)의 작성자 표시가 깨지지 않아야 하므로 닉네임은 남긴다.
        assertThat(user.getNickname()).isEqualTo("tester");
        verify(userDeviceTokenRepository).deleteAllByUser_Id(1L);
        verify(s3ImageService).delete("https://s3-url/avatar.jpg");
    }

    @Test
    void withdraw_LOCAL_계정은_비밀번호가_틀리면_MEMBER4006() {
        User user = createUser();
        UserRequestDTO.WithdrawRequest request = new UserRequestDTO.WithdrawRequest();
        request.setPassword("wrongPassword");

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> userCommandService.withdraw(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.MEMBER4006);
        assertThat(user.isDeleted()).isFalse();
        verifyNoInteractions(userDeviceTokenRepository, s3ImageService);
    }

    @Test
    void withdraw_LOCAL_계정은_비밀번호를_안_보내면_MEMBER4006() {
        User user = createUser();
        UserRequestDTO.WithdrawRequest request = new UserRequestDTO.WithdrawRequest();

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> userCommandService.withdraw(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.MEMBER4006);
        assertThat(user.isDeleted()).isFalse();
    }

    @Test
    void withdraw_소셜_계정은_비밀번호_없이_탈퇴된다() {
        User user = User.builder()
                .email("social@test.com")
                .passwordHash(null)
                .nickname("socialTester")
                .provider(Provider.KAKAO)
                .providerId("kakao-1")
                .build();
        UserRequestDTO.WithdrawRequest request = new UserRequestDTO.WithdrawRequest();

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        UserResponseDTO.WithdrawResult result = userCommandService.withdraw(1L, request);

        assertThat(result).isNotNull();
        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getProviderId()).isNull();
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(s3ImageService);
    }

    @Test
    void withdraw_존재하지_않거나_이미_탈퇴한_유저면_MEMBER4005() {
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> userCommandService.withdraw(1L, new UserRequestDTO.WithdrawRequest())
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.MEMBER4005);
        verifyNoInteractions(userDeviceTokenRepository, s3ImageService);
    }
}
