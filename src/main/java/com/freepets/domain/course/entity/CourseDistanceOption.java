package com.freepets.domain.course.entity;

/**
 * 코스 스톱 간 허용 최대 거리 선택지(preset/liked/similar 공통). 연속값(예: 임의의 미터 수)을
 * 그대로 받으면, preset은 이 값도 캐시 키에 들어가는데 사용자마다 다른 값을 보내면 캐시 적중이
 * 거의 없어져 캐시가 사실상 무의미해진다. 고정된 구간만 허용하면 지역×테마×거리 조합이 늘어도
 * 캐시가 여전히 유효하다 — liked/similar는 캐시를 안 쓰지만 UI 일관성(같은 슬라이더)을 위해
 * 똑같은 선택지를 쓴다.
 */
public enum CourseDistanceOption {

    ONE_KM(1_000, "1km"),
    FIVE_KM(5_000, "5km"),
    TEN_KM(10_000, "10km"),
    TWENTY_KM(20_000, "20km"),
    THIRTY_KM(30_000, "30km");

    private final int meters;
    private final String label;

    CourseDistanceOption(
            int meters,
            String label
    ) {
        this.meters = meters;
        this.label = label;
    }

    public int getMeters() {
        return meters;
    }

    public String getLabel() {
        return label;
    }

}
