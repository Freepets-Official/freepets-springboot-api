package com.freepets.domain.gamification.service;

/**
 * 레벨 ↔ 누적 경험치 변환. 순수 정적 유틸리티(Spring 빈 아님) —
 * {@code CalendarOccurrenceCalculator}류와 같은 결로, Mockito 없이 바로 단위 테스트할 수 있다.
 *
 * <p>레벨 L을 찍기 위한 누적 경험치는 삼각수 공식 {@code 100 × L × (L-1) / 2}를 쓴다 — 레벨이
 * 오를수록 다음 레벨까지 필요한 XP가 100씩 더 늘어나는 체증형 곡선이다(기획팀 결정: "갈수록 더
 * 필요"). 레벨 1은 0XP.
 *
 * <p>최대 레벨은 40(freepets-docs PR #49로 확정, 기존 70에서 하향) — 레벨은 계정(집사) 하나뿐이고
 * 반려동물은 레벨을 갖지 않는다는 구조 확정에 맞춰, 계정 레벨 자체도 "쉽게 안 오르되 상한은
 * 있다"는 기존 방향을 유지한 채 상한만 재조정했다.
 * {@link com.freepets.domain.gamification.entity.LevelTier}가 색(7색+무지개, 8슬롯) × 투명도
 * (5단계) = 40단계라 그 곱과 정확히 맞아떨어지게 잡았다 — 레벨 40 도달에 누적 78,000XP가
 * 필요하다.
 *
 * <p>두 상수(XP_PER_LEVEL_STEP, MAX_LEVEL) 모두 기획팀이 방향만 정하고 정확한 값은 엔지니어링
 * 제안이라 여기 한 곳만 바꾸면 곡선 전체가 조정된다.
 */
public final class LevelCurve {

    public static final int MAX_LEVEL = 40;

    // 레벨 L에서 L+1로 가는 데 필요한 XP는 XP_PER_LEVEL_STEP × L이다(예: 1→2는 100, 2→3은 200 ...).
    private static final long XP_PER_LEVEL_STEP = 100;

    private LevelCurve() {}

    /** 레벨 {@code level}(1 이상)을 찍기 위한 누적 경험치. 레벨 1은 0XP. MAX_LEVEL을 넘는 값은
     * MAX_LEVEL에서 클램핑한다(그 이상은 레벨이 더 안 오르므로 필요 XP도 그대로). */
    public static long xpToReachLevel(int level) {
        if (level <= 1) {
            return 0;
        }
        int cappedLevel = Math.min(level, MAX_LEVEL);
        long steps = (long) cappedLevel * (cappedLevel - 1);
        return XP_PER_LEVEL_STEP * steps / 2;
    }

    /** 누적 경험치로 현재 레벨을 계산한다. MAX_LEVEL에서 멈춘다 — 그 이상 쌓여도 레벨은 더
     * 오르지 않는다(negative/overflow 걱정 없는 선형 탐색, 최대 40번 반복이라 성능상 문제 없음). */
    public static int levelForTotalXp(long totalXp) {
        int level = 1;
        while (level < MAX_LEVEL && totalXp >= xpToReachLevel(level + 1)) {
            level++;
        }
        return level;
    }

    /**
     * 화면에 보여줄 레벨을 MAX_LEVEL로 잘라낸다. 레벨 상한이 70에서 40으로 내려오면서(PR #49),
     * 이미 41~70레벨을 찍은 기존 계정의 {@code User.level} 컬럼은 다음 XP 지급 전까지 옛 값
     * 그대로 남는다(레벨은 지급 시점에만 재계산해 저장하는 캐시값이라, 상한을 낮춘다고 기존
     * 행이 저절로 갱신되지 않는다). 그 값을 그대로 내려주면 응답의 {@code level}이 40을 넘어
     * {@link com.freepets.domain.gamification.entity.LevelTier}(40단계 가정)로 티어를 직접
     * 계산하는 앱이 범위 밖 값을 받는다 — 조회 응답을 만들 때는 항상 이 값을 쓴다.
     */
    public static int clampLevel(int level) {
        return Math.min(level, MAX_LEVEL);
    }

}
