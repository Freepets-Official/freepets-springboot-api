package com.freepets.domain.user.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 탈퇴 시 폐기하지 못한 애플 토큰을 매일 다시 시도한다.
 *
 * <p>이 배치가 없으면 애플이 잠깐 흔들린 순간에 탈퇴한 계정은 <b>영영 폐기되지 않는다</b> —
 * 탈퇴는 한 번뿐이라 다시 시도할 계기가 생기지 않기 때문이다. 그러면 심사 지침 5.1.1(v)를
 * 지키지 못한 계정이 조용히 쌓인다.
 *
 * <p>{@code FacilitySyncScheduler}(03:00)·{@code CoursePresetScheduler}(03:30) 다음 시각에
 * 돌린다. 로컬·개발 환경에서 실수로 실제 폐기가 일어나지 않도록 기본은 꺼져 있고, 운영에서만
 * {@code app.apple-revoke-retry.scheduling.enabled=true}로 켠다.
 *
 * <p>위 두 스케줄러와 같은 이유로 분산 락이 없다 — 여러 인스턴스로 확장하면 추가해야 한다.
 * 다만 폐기는 멱등해서(이미 무효한 토큰은 성공으로 처리된다) 중복 실행이 데이터를 망가뜨리지는
 * 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.apple-revoke-retry.scheduling", name = "enabled", havingValue = "true")
public class AppleRevokeRetryScheduler {

    private final AppleRefreshTokenService appleRefreshTokenService;

    /** 매일 새벽 4시(KST) — 기존 배치들이 끝난 뒤. */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void retryFailedRevocations() {
        int revokedCount = appleRefreshTokenService.retryFailedRevocations();

        if (revokedCount > 0) {
            log.info("밀려 있던 애플 토큰 {}건을 폐기했습니다.", revokedCount);
        }
    }

}
