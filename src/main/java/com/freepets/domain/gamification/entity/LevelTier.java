package com.freepets.domain.gamification.entity;

/**
 * 그 레벨의 발바닥 배지. 레벨 하나마다 세 축이 같이 바뀐다 — {@link PawColor}(7색)가 매
 * 레벨 한 칸씩 무지개 순서로 바뀌고, 7색을 다 돌면(7레벨마다) {@link PawFinish}(5단계)가
 * 한 칸 올라가 도장이 더 선명해지고, 그 5단계를 다 거치면(35레벨마다) {@link PawAnimal}이
 * 바뀐다. 2종 × 5단계 × 7색 = 70단계로, 레벨 상한(70)과 정확히 맞아떨어져 레벨 1~70이 전부
 * 서로 다른 배지를 가진다.
 *
 * <p>구간(min~max)을 갖는 열거형이 아니라 레벨 하나당 값 하나를 그때그때 계산하는 값 객체다 —
 * 70개를 일일이 나열하는 대신 {@link #of}가 나눗셈·나머지로 계산한다.
 *
 * <p>실제 배지 이미지는 아직 없어서 {@code badgeImageUrl}은 항상 {@code null}이다 — 디자인
 * 리소스가 오면 {@link #badgeImageUrlFor}에 (동물, 단계, 색) 조합 → URL 매핑만 채우면 되고
 * 마이그레이션은 필요 없다.
 */
public record LevelTier(
        int level,
        PawAnimal animal,
        PawFinish finish,
        PawColor color,
        String badgeImageUrl
) {

    private static final PawColor[] COLORS = PawColor.values();
    private static final PawFinish[] FINISHES = PawFinish.values();
    private static final PawAnimal[] ANIMALS = PawAnimal.values();
    private static final int LEVELS_PER_FINISH = COLORS.length;
    private static final int LEVELS_PER_ANIMAL = LEVELS_PER_FINISH * FINISHES.length;

    /** 1 미만인 레벨은 방어적으로 레벨 1로 취급한다 — 절대 예외를 던지거나 null을 반환하지 않는다. */
    public static LevelTier of(int level) {
        int safeLevel = Math.max(level, 1);
        int index = safeLevel - 1;

        PawColor color = COLORS[index % COLORS.length];
        PawFinish finish = FINISHES[(index / LEVELS_PER_FINISH) % FINISHES.length];
        PawAnimal animal = ANIMALS[(index / LEVELS_PER_ANIMAL) % ANIMALS.length];

        return new LevelTier(safeLevel, animal, finish, color, badgeImageUrlFor(animal, finish, color));
    }

    private static String badgeImageUrlFor(
            PawAnimal animal,
            PawFinish finish,
            PawColor color
    ) {
        return null;
    }

    /** "고양이 발바닥 · 빛남 · 빨강"처럼 프론트가 텍스트로 바로 쓸 수 있는 조합 라벨. 실제
     * 배지는 아이콘(발 모양+질감+색)으로 보여줄 테니 이 문자열은 대체 텍스트/접근성 용도에
     * 가깝다. */
    public String label() {
        return animal.getLabel() + " 발바닥 · " + finish.getLabel() + " · " + color.getLabel();
    }

}
