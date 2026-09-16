package com.freepets.domain.gamification.entity;

/**
 * 레벨 배지의 발바닥 질감 단계. 동물(2종)만으로는 70단계를 채울 수 없어서, 같은 발바닥 도장이
 * 레벨이 오를수록 점점 선명해지는 5단계를 색상(7색)과 곱해 70단계(2×5×7)를 채운다 — 카트라이더
 * 라이선스 장갑이 등급마다 통째로 새 그림이 아니라 같은 장갑에 디테일이 쌓이는 것과 같은 결이다.
 *
 * <p>{@link LevelTier}가 7레벨(색 한 바퀴)마다 이 단계를 한 칸씩 올린다.
 */
public enum PawFinish {

    DIM("흐릿함"),
    CLEAR("또렷함"),
    GLOSSY("빛남"),
    SPARKLE("반짝임"),
    HOLOGRAPHIC("홀로그램");

    private final String label;

    PawFinish(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
