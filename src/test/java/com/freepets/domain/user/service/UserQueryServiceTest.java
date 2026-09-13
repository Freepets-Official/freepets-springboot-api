package com.freepets.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;
import com.freepets.domain.user.dto.UserRequestDTO;
import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.entity.Profile;
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
    private FacilityOwnerClaimRepository facilityOwnerClaimRepository;

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
        User user = User.builder()
                .email("test@test.com")
                .passwordHash("encodedPassword")
                .nickname("tester")
                .provider(Provider.LOCAL)
                .build();
        // 응답에 userId를 담으므로 식별자가 있어야 한다 — 영속화 전 엔티티는 id가 null이다.
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
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

        assertThat(result.userId()).isEqualTo("1");
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

    @Test
    void login_소셜_가입자는_비밀번호_로그인으로_들어올_수_없다() {
        UserRequestDTO.LoginRequest request = createLoginRequest();
        // 소셜 가입자는 passwordHash가 null이다. matches()에 null을 넘기면 구현체에 따라
        // NPE가 나 500이 되므로, 그 전에 MEMBER4006으로 걸러야 한다.
        User socialUser = User.builder()
                .email(request.getEmail())
                .nickname("홍길동")
                .provider(Provider.KAKAO)
                .providerId("kakao-1")
                .build();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(socialUser));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> userQueryService.login(request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.MEMBER4006);
        verifyNoInteractions(passwordEncoder, jwtProvider);
    }

    @Test
    void getAccount_소유_매장이_없으면_소비자_프로필만_반환한다() {
        User user = createUser();

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(facilityOwnerClaimRepository.findFacilityIdsByUserId(1L)).thenReturn(List.of());

        UserResponseDTO.AccountResult result = userQueryService.getAccount(1L);

        assertThat(result.userId()).isEqualTo("1");
        assertThat(result.nickname()).isEqualTo(user.getNickname());
        assertThat(result.avatarUri()).isEqualTo(user.getAvatarUri());
        assertThat(result.profiles()).containsExactly(Profile.CONSUMER);
        assertThat(result.ownedFacilityIds()).isEmpty();
    }

    @Test
    void getAccount_소유_매장이_있으면_사업자_프로필과_소유_매장을_함께_반환한다() {
        User user = createUser();

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(facilityOwnerClaimRepository.findFacilityIdsByUserId(1L)).thenReturn(List.of(6L, 9L));

        UserResponseDTO.AccountResult result = userQueryService.getAccount(1L);

        assertThat(result.profiles()).containsExactly(Profile.CONSUMER, Profile.OWNER);
        assertThat(result.ownedFacilityIds()).containsExactly(6L, 9L);
    }

    @Test
    void getAccount_존재하지_않는_유저면_예외를_던진다() {
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> userQueryService.getAccount(1L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.MEMBER4005);
        verifyNoInteractions(facilityOwnerClaimRepository);
    }
}
