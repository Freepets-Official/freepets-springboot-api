package com.freepets.domain.gamification.entity;

/**
 * 그 레벨의 발바닥 배지. 색(7색+무지개, 8슬롯)이 바깥 축이고 투명도(80→60→40→20→0%, 5단계)가
 * 안쪽 축이다 — 한 색 안에서 5레벨 동안 투명도가 옅어지다가, 다섯 칸을 다 채우면(5레벨마다) 다음
 * 색으로 넘어간다. 7색(35레벨)을 다 돌면 마지막 슬롯인 {@link PawColor#RAINBOW}가 만렙(40)까지
 * 다섯 레벨을 채운다. 8슬롯 × 5단계 = 40단계로, 레벨 상한(40)과 정확히 맞아떨어진다.
 *
 * <p>구간(min~max)을 갖는 열거형이 아니라 레벨 하나당 값 하나를 그때그때 계산하는 값 객체다 —
 * 나눗셈·나머지로 계산한다.
 *
 * <p>이 값(특히 {@code label})은 앱이 참고하지 않는다 — 발바닥 색·투명도는 앱이 레벨 숫자로 직접
 * 계산해서 그리므로(반려동물 모양은 사용자가 고르는 값이라 서버가 모른다), 여기서 계산하는 값은
 * 참고용/과거 호환 필드로만 취급한다. 실제 배지 이미지는 아직 없어서 {@code badgeImageUrl}은 항상
 * {@code null}이다.
 */
public record LevelTier(
        int level,
        PawColor color,
        int opacityPercent,
        String label,
        String badgeImageUrl
) {

    private static final PawColor[] COLORS = PawColor.values();
    private static final int LEVELS_PER_COLOR = 5;
    private static final int MAX_INDEX = COLORS.length * LEVELS_PER_COLOR - 1;

    /** 1 미만인 레벨은 방어적으로 레벨 1로, 40을 넘는 레벨은 40으로 클램핑한다 — 절대 예외를
     * 던지거나 null을 반환하지 않는다. */
    public static LevelTier of(int level) {
        int safeLevel = Math.max(level, 1);
        int index = Math.min(safeLevel - 1, MAX_INDEX);

        PawColor color = COLORS[index / LEVELS_PER_COLOR];
        int opacityPercent = 80 - (index % LEVELS_PER_COLOR) * 20;

        return new LevelTier(safeLevel, color, opacityPercent, labelFor(color, opacityPercent), null);
    }

    private static String labelFor(
            PawColor color,
            int opacityPercent
    ) {
        return color.getLabel() + " " + opacityPercent + "% 발바닥";
    }

}
