package com.freepets.domain.facility.entity;

/**
 * 시설의 반려동물 동반 조건 정보를 얼마나 믿을 수 있는지. 리뷰 기반 친화도 등급({@link PetFriendlyGrade},
 * "여기가 반려동물 다니기 좋은 곳인지")과는 다른 축이다 — 이건 "여기 적힌 조건 자체가 맞는지"를 나타낸다.
 *
 * <p>프론트({@code data/types.ts})와 값을 맞췄다. 사용자에게는 {@code CONFIRMED}(확정)와 나머지
 * 3개를 묶은 "확인 필요"로만 보여주지만, 근거({@link ConfidenceSource})를 구분해야 해서 서버는
 * 4단계 그대로 들고 있는다.
 */
public enum Confidence {
    CONFIRMED,
    LIKELY,
    ESTIMATED,
    UNVERIFIED;

    /** {@link #of}가 함께 내려주는 신뢰도 + 근거 한 쌍. 둘은 항상 같은 판단에서 나오므로 따로 계산하면 어긋날 수 있다. */
    public record View(
            Confidence confidence,
            ConfidenceSource source
    ) {}

    /**
     * 신뢰도는 저장하지 않고 조회 시점에 계산한다(F4). 실제로 낼 수 있는 신호가 지금은 이 둘뿐이다:
     * 최근 1주 내 실시간 거부 제보가 있는지, 관광공사 원문이라도 있는지. 사업자 셀프 등록·방문자
     * 다수 일치·직접 전화 확인은 그 기능 자체가 없어 이 메서드가 절대 그 값을 내지 않는다.
     *
     * @param petConditionRaw      시설에 정리돼 있는 조건 안내문. 비어있으면(공백 포함) 신호로 안 침
     * @param recentDenialReportCount 최근(FacilityReport.RECENT_WINDOW_DAYS) 실시간 거부 제보 수
     */
    public static View of(
            String petConditionRaw,
            long recentDenialReportCount
    ) {
        if (recentDenialReportCount > 0) {
            return new View(UNVERIFIED, ConfidenceSource.DENIAL_REPORT);
        }

        boolean hasCuratedCondition = petConditionRaw != null && !petConditionRaw.isBlank();
        return hasCuratedCondition
                ? new View(ESTIMATED, ConfidenceSource.PARSED)
                : new View(UNVERIFIED, ConfidenceSource.NONE);
    }
}
