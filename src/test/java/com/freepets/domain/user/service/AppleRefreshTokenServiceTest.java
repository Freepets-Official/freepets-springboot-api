package com.freepets.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.user.entity.AppleRefreshToken;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.AppleRefreshTokenRepository;
import com.freepets.global.crypto.AppleTokenCipher;
import com.freepets.infra.oauth.AppleTokenClient;
import com.freepets.infra.oauth.OAuthException;

@ExtendWith(MockitoExtension.class)
class AppleRefreshTokenServiceTest {

    private static final Long USER_ID = 1L;
    private static final String AUTHORIZATION_CODE = "apple-auth-code";
    private static final String REFRESH_TOKEN = "r.AppleRefreshToken";
    private static final String ENCRYPTED_TOKEN = "encrypted-apple-refresh-token";

    @Mock
    private AppleRefreshTokenRepository appleRefreshTokenRepository;

    @Mock
    private AppleTokenCipher appleTokenCipher;

    @Mock
    private AppleTokenClient appleTokenClient;

    @Mock
    private ObjectProvider<AppleTokenClient> appleTokenClientProvider;

    private AppleRefreshTokenService appleRefreshTokenService;

    @BeforeEach
    void setUp() {
        appleRefreshTokenService = new AppleRefreshTokenService(
                appleRefreshTokenRepository,
                appleTokenCipher,
                appleTokenClientProvider
        );
    }

    private void givenAppleConfigured() {
        lenient().when(appleTokenClientProvider.getIfAvailable()).thenReturn(appleTokenClient);
        lenient().when(appleTokenCipher.isEnabled()).thenReturn(true);
    }

    private void givenAppleNotConfigured() {
        lenient().when(appleTokenClientProvider.getIfAvailable()).thenReturn(null);
    }

    private User createUser() {
        User user = User.builder()
                .email("test@test.com")
                .nickname("tester")
                .provider(Provider.APPLE)
                .providerId("apple-sub")
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private AppleRefreshToken createStoredToken(User user) {
        return AppleRefreshToken.builder()
                .user(user)
                .encryptedRefreshToken(ENCRYPTED_TOKEN)
                .build();
    }

    // ---------- 보관 ----------

    @Test
    void 인가_코드를_교환해_암호화한_뒤_저장한다() {
        givenAppleConfigured();
        User user = createUser();
        when(appleTokenClient.exchangeAuthorizationCode(AUTHORIZATION_CODE)).thenReturn(REFRESH_TOKEN);
        when(appleTokenCipher.encrypt(REFRESH_TOKEN)).thenReturn(ENCRYPTED_TOKEN);
        when(appleRefreshTokenRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

        appleRefreshTokenService.storeFromAuthorizationCode(user, AUTHORIZATION_CODE);

        ArgumentCaptor<AppleRefreshToken> captor = ArgumentCaptor.forClass(AppleRefreshToken.class);
        verify(appleRefreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedRefreshToken()).isEqualTo(ENCRYPTED_TOKEN);
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    void 이미_보관된_토큰이_있으면_새_토큰으로_갈아끼운다() {
        givenAppleConfigured();
        User user = createUser();
        AppleRefreshToken stored = createStoredToken(user);
        stored.recordRevokeFailure("이전 실패");

        when(appleTokenClient.exchangeAuthorizationCode(AUTHORIZATION_CODE)).thenReturn(REFRESH_TOKEN);
        when(appleTokenCipher.encrypt(REFRESH_TOKEN)).thenReturn("새-암호문");
        when(appleRefreshTokenRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(stored));

        appleRefreshTokenService.storeFromAuthorizationCode(user, AUTHORIZATION_CODE);

        verify(appleRefreshTokenRepository, never()).save(any());
        assertThat(stored.getEncryptedRefreshToken()).isEqualTo("새-암호문");
        assertThat(stored.getRevokeRequestedAt()).isNull();
        assertThat(stored.getRevokeAttemptCount()).isZero();
    }

    // 인가 코드를 아직 안 보내는 구버전 앱. 정상 상황이라 아무 일도 일어나지 않아야 한다.
    @Test
    void 인가_코드가_없으면_아무것도_하지_않는다() {
        appleRefreshTokenService.storeFromAuthorizationCode(createUser(), null);
        appleRefreshTokenService.storeFromAuthorizationCode(createUser(), "   ");

        verifyNoInteractions(appleRefreshTokenRepository, appleTokenClient);
    }

    @Test
    void 애플_설정이_없으면_보관을_건너뛴다() {
        givenAppleNotConfigured();

        appleRefreshTokenService.storeFromAuthorizationCode(createUser(), AUTHORIZATION_CODE);

        verifyNoInteractions(appleRefreshTokenRepository);
    }

    // 애플 장애로 로그인 자체가 막히면 안 된다.
    @Test
    void 교환에_실패해도_예외를_던지지_않는다() {
        givenAppleConfigured();
        when(appleTokenClient.exchangeAuthorizationCode(anyString()))
                .thenThrow(new OAuthException("애플이 인가 코드를 거부했습니다."));

        assertDoesNotThrow(
                () -> appleRefreshTokenService.storeFromAuthorizationCode(createUser(), AUTHORIZATION_CODE)
        );
        verify(appleRefreshTokenRepository, never()).save(any());
    }

    // ---------- 폐기 ----------

    @Test
    void 탈퇴하면_애플에_폐기를_요청하고_행을_지운다() {
        givenAppleConfigured();
        AppleRefreshToken stored = createStoredToken(createUser());
        when(appleRefreshTokenRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(stored));
        when(appleTokenCipher.decrypt(ENCRYPTED_TOKEN)).thenReturn(REFRESH_TOKEN);

        appleRefreshTokenService.revokeForWithdrawal(USER_ID);

        verify(appleTokenClient).revokeRefreshToken(REFRESH_TOKEN);
        verify(appleRefreshTokenRepository).delete(stored);
    }

    @Test
    void 보관된_토큰이_없으면_폐기를_건너뛴다() {
        when(appleRefreshTokenRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

        appleRefreshTokenService.revokeForWithdrawal(USER_ID);

        verifyNoInteractions(appleTokenClient);
    }

    // 애플 장애로 탈퇴가 막히면 안 된다. 대신 재시도 대상으로 남긴다.
    @Test
    void 폐기에_실패해도_예외를_던지지_않고_실패를_기록한다() {
        givenAppleConfigured();
        AppleRefreshToken stored = createStoredToken(createUser());
        when(appleRefreshTokenRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(stored));
        when(appleTokenCipher.decrypt(anyString())).thenReturn(REFRESH_TOKEN);
        doThrow(new OAuthException("애플 서버가 응답하지 않습니다."))
                .when(appleTokenClient).revokeRefreshToken(anyString());

        assertDoesNotThrow(() -> appleRefreshTokenService.revokeForWithdrawal(USER_ID));

        assertThat(stored.getRevokeRequestedAt()).isNotNull();
        assertThat(stored.getRevokeAttemptCount()).isEqualTo(1);
        assertThat(stored.getRevokeFailedReason()).contains("응답하지 않습니다");
        verify(appleRefreshTokenRepository, never()).delete(any());
    }

    @Test
    void 애플_설정이_없으면_폐기를_재시도_대상으로_남긴다() {
        givenAppleNotConfigured();
        AppleRefreshToken stored = createStoredToken(createUser());
        when(appleRefreshTokenRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(stored));

        assertDoesNotThrow(() -> appleRefreshTokenService.revokeForWithdrawal(USER_ID));

        assertThat(stored.getRevokeRequestedAt()).isNotNull();
        verify(appleRefreshTokenRepository, never()).delete(any());
    }

    // ---------- 재시도 ----------

    @Test
    void 밀린_폐기를_다시_시도해_성공하면_행을_지운다() {
        givenAppleConfigured();
        AppleRefreshToken stored = createStoredToken(createUser());
        stored.recordRevokeFailure("일시적 실패");
        when(appleRefreshTokenRepository.findAllByRevokeRequestedAtIsNotNull()).thenReturn(List.of(stored));
        when(appleTokenCipher.decrypt(ENCRYPTED_TOKEN)).thenReturn(REFRESH_TOKEN);

        assertThat(appleRefreshTokenService.retryFailedRevocations()).isEqualTo(1);

        verify(appleRefreshTokenRepository).delete(stored);
    }

    @Test
    void 재시도_상한을_넘긴_토큰은_건너뛴다() {
        AppleRefreshToken stored = createStoredToken(createUser());
        for (int attempt = 0; attempt < 10; attempt++) {
            stored.recordRevokeFailure("계속 실패");
        }
        when(appleRefreshTokenRepository.findAllByRevokeRequestedAtIsNotNull()).thenReturn(List.of(stored));

        assertThat(appleRefreshTokenService.retryFailedRevocations()).isZero();

        verifyNoInteractions(appleTokenClient);
    }
}
