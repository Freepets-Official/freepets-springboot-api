package com.freepets.domain.user.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.user.entity.AppleRefreshToken;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.AppleRefreshTokenRepository;
import com.freepets.global.crypto.AppleTokenCipher;
import com.freepets.infra.oauth.AppleTokenClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 애플 refresh token을 보관했다가 계정 삭제 시 애플에 폐기를 요청한다.
 *
 * <p>App Store 심사 지침 5.1.1(v)는 Sign in with Apple을 쓰는 앱이 계정을 삭제할 때 애플 토큰까지
 * 폐기하도록 요구한다. 폐기하려면 refresh token이 있어야 하고, 그건 로그인 때 앱이 보내주는
 * 인가 코드를 교환해야만 얻을 수 있어서 로그인 시점에 미리 받아둔다.
 *
 * <p><b>이 클래스의 공개 메소드는 예외를 밖으로 던지지 않는다.</b> 애플과의 통신 실패가 로그인이나
 * 탈퇴를 막으면 안 되기 때문이다 — 애플이 잠깐 흔들렸다고 사용자가 탈퇴를 못 하는 쪽이 훨씬 나쁘다.
 * 대신 실패를 행에 기록해 {@link AppleRevokeRetryScheduler}가 나중에 다시 시도한다.
 *
 * <p><b>애플 설정이 없으면 통째로 비활성이다.</b> 키 발급 전에도 서버는 떠야 하므로, 이 경우
 * 경고만 남기고 조용히 넘어간다({@code OAuthConfig} 참고). 그래서 의존성을 {@link ObjectProvider}로
 * 받는다 — 빈이 아예 등록되지 않은 상태를 정상으로 다루기 위해서다.
 *
 * <p>네트워크 호출이 트랜잭션 안에서 일어난다. {@code UserCommandService.withdraw}가 S3 삭제를
 * 트랜잭션 안에서 하는 것과 같은 선택이며, 호출에 5초·10초 타임아웃이 걸려 있어 커넥션을 잡는
 * 시간이 유계다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AppleRefreshTokenService {

    /** 애플이 계속 거부하는 토큰을 매일 무한정 재시도하지 않도록 상한을 둔다. */
    private static final int MAX_REVOKE_ATTEMPT_COUNT = 10;

    private final AppleRefreshTokenRepository appleRefreshTokenRepository;
    private final AppleTokenCipher appleTokenCipher;
    private final ObjectProvider<AppleTokenClient> appleTokenClientProvider;

    /**
     * 로그인 시 받은 인가 코드를 refresh token으로 바꿔 보관한다.
     *
     * <p>교환에 실패해도 로그인은 그대로 진행시킨다. 실패하면 나중에 폐기할 토큰이 없어지지만,
     * 애플 장애로 로그인 자체가 막히는 것보다는 낫다.
     */
    public void storeFromAuthorizationCode(
            User user,
            String authorizationCode
    ) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            // authorizationCode를 아직 보내지 않는 구버전 앱. 정상 상황이라 경고로 올리지 않는다.
            log.debug("애플 로그인에 authorizationCode가 없어 토큰 보관을 건너뜁니다. userId={}", user.getId());
            return;
        }

        AppleTokenClient appleTokenClient = appleTokenClientProvider.getIfAvailable();
        if (appleTokenClient == null || !appleTokenCipher.isEnabled()) {
            log.warn("애플 토큰 설정이 없어 보관을 건너뜁니다. 계정 삭제 시 애플 토큰을 폐기할 수 없습니다. userId={}",
                    user.getId());
            return;
        }

        try {
            String refreshToken = appleTokenClient.exchangeAuthorizationCode(authorizationCode);
            saveOrReplace(user, appleTokenCipher.encrypt(refreshToken));
        } catch (RuntimeException exception) {
            log.warn("애플 인가 코드 교환에 실패했습니다. userId={}", user.getId(), exception);
        }
    }

    /**
     * 탈퇴하면서 애플에 토큰 폐기를 요청한다.
     *
     * <p>성공하면 보관하던 행을 지운다. 실패하면 행을 남긴 채 실패를 기록해 재시도 대상으로
     * 만든다 — 탈퇴 자체는 어떤 경우에도 계속 진행된다.
     *
     * <p><b>{@code REQUIRES_NEW}가 반드시 필요하다.</b> 이 메소드는 탈퇴 트랜잭션이 커밋된 뒤
     * {@link AppleTokenRevocationListener}가 부른다. 그 시점에도 원래 트랜잭션은 아직 "정리 중"
     * 상태로 묶여 있어서, 기본 전파({@code REQUIRED})로 두면 끝나가는 그 트랜잭션에 합류해
     * <b>여기서 한 쓰기가 예외 없이 조용히 사라진다</b> — 실패를 기록해도 재시도 대상으로 남지
     * 않는다는 뜻이다. 새 트랜잭션을 열어야 기록과 삭제가 실제로 커밋된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeForWithdrawal(Long userId) {
        Optional<AppleRefreshToken> stored = appleRefreshTokenRepository.findByUser_Id(userId);
        if (stored.isEmpty()) {
            // 앱이 인가 코드를 보내기 전에 가입했거나 교환에 실패했던 계정. 폐기할 대상이 없다.
            log.info("보관된 애플 토큰이 없어 폐기를 건너뜁니다. userId={}", userId);
            return;
        }

        revoke(stored.get());
    }

    /**
     * 폐기하지 못한 채 남아 있는 토큰을 다시 시도한다.
     *
     * @return 이번에 폐기에 성공한 건수
     */
    public int retryFailedRevocations() {
        List<AppleRefreshToken> pending = appleRefreshTokenRepository.findAllByRevokeRequestedAtIsNotNull();

        int revokedCount = 0;
        for (AppleRefreshToken appleRefreshToken : pending) {
            if (appleRefreshToken.getRevokeAttemptCount() >= MAX_REVOKE_ATTEMPT_COUNT) {
                log.error("애플 토큰 폐기가 {}회 실패해 재시도를 멈춥니다. 수동 확인이 필요합니다. userId={}, reason={}",
                        appleRefreshToken.getRevokeAttemptCount(),
                        appleRefreshToken.getUser().getId(),
                        appleRefreshToken.getRevokeFailedReason());
                continue;
            }

            if (revoke(appleRefreshToken)) {
                revokedCount++;
            }
        }
        return revokedCount;
    }

    /** @return 폐기에 성공했는지. 실패하면 행에 사유를 기록해 남긴다 */
    private boolean revoke(AppleRefreshToken appleRefreshToken) {
        Long userId = appleRefreshToken.getUser().getId();

        AppleTokenClient appleTokenClient = appleTokenClientProvider.getIfAvailable();
        if (appleTokenClient == null || !appleTokenCipher.isEnabled()) {
            appleRefreshToken.recordRevokeFailure("애플 토큰 설정이 없어 폐기를 시도하지 못했습니다.");
            log.warn("애플 토큰 설정이 없어 폐기를 건너뜁니다. 설정을 채운 뒤 재시도됩니다. userId={}", userId);
            return false;
        }

        try {
            appleTokenClient.revokeRefreshToken(appleTokenCipher.decrypt(appleRefreshToken.getEncryptedRefreshToken()));
        } catch (RuntimeException exception) {
            appleRefreshToken.recordRevokeFailure(exception.getMessage());
            log.warn("애플 토큰 폐기에 실패했습니다. 탈퇴는 그대로 진행되며 나중에 재시도됩니다. userId={}",
                    userId, exception);
            return false;
        }

        // 폐기된 토큰은 더 들고 있을 이유가 없다.
        appleRefreshTokenRepository.delete(appleRefreshToken);
        log.info("애플 토큰을 폐기했습니다. userId={}", userId);
        return true;
    }

    private void saveOrReplace(
            User user,
            String encryptedRefreshToken
    ) {
        appleRefreshTokenRepository.findByUser_Id(user.getId()).ifPresentOrElse(
                stored -> stored.replaceToken(encryptedRefreshToken),
                () -> appleRefreshTokenRepository.save(
                        AppleRefreshToken.builder()
                                .user(user)
                                .encryptedRefreshToken(encryptedRefreshToken)
                                .build()
                )
        );
    }
}
