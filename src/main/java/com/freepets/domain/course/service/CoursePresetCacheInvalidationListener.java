package com.freepets.domain.course.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.freepets.domain.facility.event.FacilityBecameIneligibleEvent;

import lombok.RequiredArgsConstructor;

/**
 * facility 도메인이 발행하는 {@link FacilityBecameIneligibleEvent}를 구독해 프리셋 코스 캐시를
 * 무효화한다. course 도메인이 facility 도메인을 몰라도 되게(반대로 facility가 course를 알 필요도
 * 없게) 이벤트로 분리했다 — 두 도메인이 서로 직접 의존하지 않는다.
 *
 * <p>{@code AFTER_COMMIT}인 이유 — 시설 갱신은 {@code FacilityUpsertService}의 트랜잭션
 * 안에서 일어난다. 그 트랜잭션이 롤백되면 시설은 실제로 자격을 잃은 게 아니므로, 커밋이 실제로
 * 끝난 뒤에만 캐시를 지워야 한다.
 */
@Component
@RequiredArgsConstructor
public class CoursePresetCacheInvalidationListener {

    private final CoursePresetService coursePresetService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFacilityBecameIneligible(FacilityBecameIneligibleEvent event) {
        coursePresetService.invalidateCoursesContaining(event.facilityId());
    }

}
