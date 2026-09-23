package com.freepets.domain.gamification.entity;

/**
 * 레벨 배지의 발바닥 색깔. 무지개 순서(빨주노초파남보) + 만렙 구간 전용 {@code RAINBOW} 여덟
 * 번째 슬롯. {@link LevelTier}가 5레벨마다 이 색을 한 칸씩 돌리고(투명도 5단계를 다 채우면
 * 다음 색으로), 7색을 다 돌면 {@code RAINBOW}로 넘어가 만렙(40)까지 채운다.
 */
public enum PawColor {

    RED("빨강"),
    ORANGE("주황"),
    YELLOW("노랑"),
    GREEN("초록"),
    BLUE("파랑"),
    INDIGO("남색"),
    VIOLET("보라"),
    RAINBOW("무지개");

    private final String label;

    PawColor(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
