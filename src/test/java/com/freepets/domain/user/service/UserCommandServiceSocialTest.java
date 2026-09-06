package com.freepets.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.infra.s3.S3ImageService;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceSocialTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private S3ImageService s3ImageService;

    @InjectMocks
    private UserCommandService userCommandService;

    private User createSocialUser() {
        return User.builder()
                .email("foo@bar.com")
                .nickname("홍길동")
                .provider(Provider.KAKAO)
                .providerId("kakao-1")
                .build();
    }

    @Test
    void providerId로_찾으면_기존_유저를_그대로_반환하고_저장하지_않는다() {
        when(userRepository.findByProviderAndProviderId(Provider.KAKAO, "kakao-1"))
                .thenReturn(Optional.of(createSocialUser()));

        SocialUserResolution resolution = userCommandService.findOrRegisterSocialUser(
                Provider.KAKAO, "kakao-1", "foo@bar.com", "홍길동"
        );

        assertThat(resolution.isNewUser()).isFalse();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void providerId가_없으면_새로_가입시킨다() {
        when(userRepository.findByProviderAndProviderId(Provider.GOOGLE, "google-1"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("foo@bar.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SocialUserResolution resolution = userCommandService.findOrRegisterSocialUser(
                Provider.GOOGLE, "google-1", "foo@bar.com", "홍길동"
        );

        assertThat(resolution.isNewUser()).isTrue();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getProvider()).isEqualTo(Provider.GOOGLE);
        assertThat(saved.getValue().getProviderId()).isEqualTo("google-1");
        assertThat(saved.getValue().getPasswordHash()).isNull();
    }

    @Test
    void 같은_이메일이_다른_경로로_이미_있으면_OAUTH4003을_던진다() {
        when(userRepository.findByProviderAndProviderId(Provider.GOOGLE, "google-1"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("foo@bar.com")).thenReturn(true);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> userCommandService.findOrRegisterSocialUser(
                        Provider.GOOGLE, "google-1", "foo@bar.com", "홍길동"
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.OAUTH4003);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void 이메일이_없으면_중복_검사를_건너뛰고_가입시킨다() {
        when(userRepository.findByProviderAndProviderId(Provider.APPLE, "apple-1"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SocialUserResolution resolution = userCommandService.findOrRegisterSocialUser(
                Provider.APPLE, "apple-1", null, "홍길동"
        );

        assertThat(resolution.isNewUser()).isTrue();
        // 플레이스홀더 이메일은 providerId 기반이라 검사할 대상이 아니다.
        verify(userRepository, never()).existsByEmail(any());
    }
}
