package com.freepets.domain.gamification.entity;

/**
 * 레벨 배지의 발바닥 모양. {@link LevelTier}가 {@link PawColor} 7색을 한 동물로 한 바퀴 돌면
 * 다음 동물로 넘어간다 — 10종 × 7색 = 70단계로, {@link com.freepets.domain.gamification.service.LevelCurve#MAX_LEVEL}
 * (70)과 정확히 맞아떨어진다(레벨 하나마다 배지가 전부 다르다).
 *
 * <p>이 앱이 실제로 지원하는 반려동물 종({@code Pet.Kind}: DOG/CAT/PARROT/RABBIT/REPTILE/
 * SMALL_ANIMAL)의 구체적인 예시 10종으로 채웠다 — 앱과 무관한 동물을 끌어오지 않기 위함이다.
 * 순서는 "동반 여행 반려동물로서 흔한 정도"로 앞에서 뒤로 갈수록 희귀해지게 배치했다(등급이
 * 오를수록 희귀해지는 흔한 게임 보상 구조와 같은 결).
 */
public enum PawAnimal {

    DOG("개"),
    CAT("고양이"),
    RABBIT("토끼"),
    HAMSTER("햄스터"),
    GUINEA_PIG("기니피그"),
    HEDGEHOG("고슴도치"),
    FERRET("페럿"),
    PARROT("앵무새"),
    TURTLE("거북이"),
    LIZARD("도마뱀");

    private final String label;

    PawAnimal(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
