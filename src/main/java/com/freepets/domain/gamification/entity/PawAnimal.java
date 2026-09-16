package com.freepets.domain.gamification.entity;

/**
 * 레벨 배지의 발바닥 동물. 이 앱이 실제 지원하는 반려동물({@code Pet.Kind})은 개·고양이·앵무새·
 * 토끼·파충류·소동물 6개 큰 분류인데, 그중에서도 실제 등록되는 압도적 다수가 개·고양이라 이
 * 둘로만 구성한다 — 도마뱀·페럿 같은 종이 레벨 배지에 등장하면 "이 앱과 무슨 상관이지" 싶은
 * 위화감이 있어서다.
 *
 * <p>{@link LevelTier}가 개(레벨 1~35) → 고양이(레벨 36~70) 순서로 딱 절반씩 나눠 쓴다.
 */
public enum PawAnimal {

    DOG("개"),
    CAT("고양이");

    private final String label;

    PawAnimal(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
