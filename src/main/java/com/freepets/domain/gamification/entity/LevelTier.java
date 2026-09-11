package com.freepets.domain.gamification.entity;

/**
 * 그 레벨의 발바닥 배지. 테일즈런너·카트라이더처럼 <b>레벨 하나마다</b> 발바닥 색이 무지개
 * 순서(빨주노초파남보)로 한 칸씩 바뀌고, 7색을 다 돌면 다음 동물로 넘어가 다시 빨강부터 돈다
 * ({@link PawAnimal} 10종 × {@link PawColor} 7색 = 70단계 — 레벨 상한(70)과 정확히 맞아떨어져
 * 레벨 1~70이 전부 서로 다른 배지를 가진다).
 *
 * <p>구간(min~max)을 갖는 열거형이 아니라 레벨 하나당 값 하나를 그때그때 계산하는 값 객체다 —
 * 70개를 일일이 나열하는 대신 {@link #of}가 나눗셈·나머지로 계산한다. 동물이나 색이 늘어나도
 * (예: 11번째 동물 추가) 이 계산 로직은 그대로 두고 {@link PawAnimal}에 상수만 추가하면 된다.
 *
 * <p>실제 배지 이미지는 아직 없어서 {@code badgeImageUrl}은 항상 {@code null}이다 — 디자인
 * 리소스가 오면 {@link #badgeImageUrlFor}에 (동물, 색) 조합 → URL 매핑만 채우면 되고
 * 마이그레이션은 필요 없다.
 */
public record LevelTier(
        int level,
        PawAnimal animal,
        PawColor color,
        String badgeImageUrl
) {

    private static final PawColor[] COLORS = PawColor.values();
    private static final PawAnimal[] ANIMALS = PawAnimal.values();

    /** 1 미만인 레벨은 방어적으로 레벨 1로 취급한다 — 절대 예외를 던지거나 null을 반환하지 않는다. */
    public static LevelTier of(int level) {
        int safeLevel = Math.max(level, 1);
        int index = safeLevel - 1;

        PawColor color = COLORS[index % COLORS.length];
        PawAnimal animal = ANIMALS[(index / COLORS.length) % ANIMALS.length];

        return new LevelTier(safeLevel, animal, color, badgeImageUrlFor(animal, color));
    }

    private static String badgeImageUrlFor(
            PawAnimal animal,
            PawColor color
    ) {
        return null;
    }

    /** "고양이 발바닥 · 보라"처럼 프론트가 텍스트로 바로 쓸 수 있는 조합 라벨. 실제 배지는
     * 아이콘(발 모양+색)으로 보여줄 테니 이 문자열은 대체 텍스트/접근성 용도에 가깝다. */
    public String label() {
        return animal.getLabel() + " 발바닥 · " + color.getLabel();
    }

}
