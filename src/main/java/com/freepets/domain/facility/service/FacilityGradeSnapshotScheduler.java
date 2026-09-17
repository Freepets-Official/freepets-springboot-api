package com.freepets.domain.facility.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 발자국 등급 추이용 스냅샷을 매일 한 번 적재한다. 사업자 대시보드의 리뷰·통계 화면이 이 스냅샷으로
 * 등급 추이 그래프를 그린다.
 *
 * <p>{@link FacilitySyncScheduler}가 새벽 3시에 시설 동기화를 마친 뒤 돈다 — 두 스케줄러가 같은
 * 시설 데이터를 다루더라도 순서를 강제하지는 않는다(스냅샷은 등급 캐시가 최신인지 여부와 무관하게
 * 그날의 현재값을 그대로 찍는다).
 *
 * <p>로컬/개발 환경에서 실수로 돌지 않도록 기본은 꺼져 있다. 운영 환경에서만
 * {@code application.yml}에 아래처럼 설정해 켠다.
 *
 * <pre>
 * app:
 *   facility-grade-snapshot:
 *     scheduling:
 *       enabled: true
 * </pre>
 *
 * <p><b>주의(현재 미해결):</b> {@link FacilitySyncScheduler}와 같은 이유로 분산 락이 없다 —
 * 단일 인스턴스 배포를 전제로 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.facility-grade-snapshot.scheduling", name = "enabled", havingValue = "true")
public class FacilityGradeSnapshotScheduler {

    private final FacilityGradeSnapshotService facilityGradeSnapshotService;

    /** 매일 새벽 3시 30분(KST) — 시설 동기화(새벽 3시) 직후. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void snapshot() {
        log.info("발자국 등급 스냅샷 적재를 시작합니다.");
        long snapshotted = facilityGradeSnapshotService.snapshotAll();
        log.info("발자국 등급 스냅샷 적재 완료. {}건", snapshotted);
    }

}
