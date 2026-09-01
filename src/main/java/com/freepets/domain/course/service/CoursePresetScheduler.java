package com.freepets.domain.course.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 캐시된 프리셋 코스(지역×테마) 전부를 매일 다시 계산한다. 리뷰가 새로 쌓이거나 시설 동반 가능
 * 여부가 바뀌면 스톱 구성이 달라질 수 있는데, {@code preset} 조회는 캐시 히트 시 스톱 목록 자체는
 * 재계산하지 않으므로(점수·거리만 매번 새로 계산) 이 배치가 없으면 첫 계산 시점의 스톱 구성이
 * 영영 고정된다.
 *
 * <p>{@link com.freepets.domain.facility.service.FacilitySyncScheduler}(매일 03:00, 시설
 * 동기화+조건 파싱) 바로 다음 시각에 돌린다 — 그날 새로 반영된 시설 데이터를 기준으로 재계산하기
 * 위함. 로컬/개발 환경에서 실수로 돌지 않도록 기본은 꺼져 있고, 운영 환경에서만
 * {@code app.course-preset.scheduling.enabled=true}로 켠다.
 *
 * <p>FacilitySyncScheduler와 같은 이유로 분산 락이 없다 — 여러 인스턴스로 확장하면 추가해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.course-preset.scheduling", name = "enabled", havingValue = "true")
public class CoursePresetScheduler {

    private final CoursePresetService coursePresetService;

    /** 매일 새벽 3시 30분(KST) — FacilitySyncScheduler(03:00) 이후. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void recalculateAll() {
        log.info("프리셋 코스 재계산을 시작합니다.");
        coursePresetService.recalculateAll();
        log.info("프리셋 코스 재계산을 마쳤습니다.");
    }

}
