package com.freepets.domain.gamification.entity;

/**
 * 레벨 배지의 발바닥 색깔. 무지개 순서(빨주노초파남보) 그대로 — 테일즈런너·카트라이더의
 * 라이선스 색깔이 레벨마다 바뀌는 방식을 참고했다. {@link LevelTier}가 레벨 하나마다 이 7색을
 * 한 칸씩 돌리고, 7색을 다 돌면 {@link PawAnimal}이 다음 동물로 넘어간다.
 */
public enum PawColor {

    RED("빨강"),
    ORANGE("주황"),
    YELLOW("노랑"),
    GREEN("초록"),
    BLUE("파랑"),
    INDIGO("남색"),
    VIOLET("보라");

    private final String label;

    PawColor(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
