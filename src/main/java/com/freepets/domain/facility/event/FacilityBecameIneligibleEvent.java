package com.freepets.domain.facility.event;

/**
 * 시설이 추천 후보 자격을 잃었을 때(비활성화되거나 동반 불가로 전환) 발행된다 —
 * {@link com.freepets.domain.facility.entity.Facility#isEligibleForRecommendation}이
 * true→false로 바뀐 시점.
 *
 * <p>facility 도메인은 이 사실을 발행만 하고, 이걸로 뭘 할지는 모른다(코스 프리셋 캐시
 * 무효화 등) — 도메인 간 결합을 피하려고 이벤트로 분리했다. 구독자는
 * {@code com.freepets.domain.course.service.CoursePresetCacheInvalidationListener} 참고.
 */
public record FacilityBecameIneligibleEvent(
        Long facilityId
) {}
