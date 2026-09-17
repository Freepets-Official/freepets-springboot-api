package com.freepets.domain.gamification.entity;

/**
 * {@link Badge}의 단계. 모든 {@link BadgeFamily}가 공통으로 이 6단계(1/5/10/50/100/500회)를 쓴다
 * — 도메인마다 단계 수·이름이 다르던 이전 방식을 통일한 결과다(Badge 클래스 주석 참고).
 */
public enum BadgeTier {

    BRONZE("동"),
    SILVER("은"),
    GOLD("금"),
    RUBY("루비"),
    CRYSTAL("크리스탈"),
    DIAMOND("다이아");

    private final String label;

    BadgeTier(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
