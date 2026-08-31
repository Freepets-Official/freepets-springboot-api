package com.freepets.domain.facility.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 시설 데이터를 매일 한 번 동기화하고, 그 결과로 새로 생기거나 아직 안 채워진(NOT_PROCESSED)
 * 조건 원문을 이어서 구조화한다.
 *
 * <p>{@code facilitySync}(관광공사 API 호출)는 비용이 없어 매일 돌려도 되지만,
 * {@code facilityConditionParse}는 Claude 호출 비용이 있다 — 다만 NOT_PROCESSED 대상만
 * 처리하므로 매일 돌아도 그날 새로 늘어난 만큼만 호출된다(전량 재호출 아님).
 *
 * <p>로컬/개발 환경에서 실수로 돌지 않도록 기본은 꺼져 있다. 운영 환경에서만
 * {@code application.yml}에 아래처럼 설정해 켠다.
 *
 * <pre>
 * app:
 *   facility-sync:
 *     scheduling:
 *       enabled: true
 * </pre>
 *
 * <p><b>주의(현재 미해결):</b> 분산 락(예: ShedLock)이 없다 — 애플리케이션을 인스턴스 여러 대로
 * 띄우면 각 인스턴스가 매일 새벽 3시에 독립적으로 동기화·파싱을 돌려 Claude를 중복 호출하고
 * 저장이 경합할 수 있다. 지금은 단일 인스턴스 배포를 전제로 한다 — 여러 대로 확장하게 되면
 * 이 클래스에 분산 락을 추가해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.facility-sync.scheduling", name = "enabled", havingValue = "true")
public class FacilitySyncScheduler {

    private final FacilitySyncService facilitySyncService;
    private final FacilityConditionLlmBatchService facilityConditionLlmBatchService;

    /** 매일 새벽 3시(KST) — 트래픽이 적은 시간대. 필요하면 조정 가능. */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void syncAndParse() {
        log.info("일일 시설 동기화를 시작합니다.");
        FacilitySyncResult syncResult = facilitySyncService.syncAll();
        log.info("시설 동기화 완료. {}", syncResult.summary());

        log.info("조건 파싱을 시작합니다.");
        FacilityConditionLlmBatchResult parseResult = facilityConditionLlmBatchService.parseAll();
        log.info("조건 파싱 완료. {}", parseResult.summary());
    }

}
