package com.freepets.domain.gamification.service;

/**
 * 레벨 ↔ 누적 경험치 변환. 순수 정적 유틸리티(Spring 빈 아님) —
 * {@code CalendarOccurrenceCalculator}류와 같은 결로, Mockito 없이 바로 단위 테스트할 수 있다.
 *
 * <p>레벨 L을 찍기 위한 누적 경험치는 삼각수 공식 {@code 100 × L × (L-1) / 2}를 쓴다 — 레벨이
 * 오를수록 다음 레벨까지 필요한 XP가 100씩 더 늘어나는 체증형 곡선이다(기획팀 결정: "갈수록 더
 * 필요"). 레벨 1은 0XP.
 *
 * <p>최대 레벨은 70 — 당근마켓 매너온도처럼 상한은 있되 쉽게 안 오르게 하자는 요청에 맞춘
 * 값이다. 원래는 활동이 나빴을 때 XP를 깎는 안도 논의했지만, 억울한 감점(허위 신고 등)을 막을
 * 승인 절차가 이 리포에 아직 없어서(제보·리뷰 신고 둘 다 관리자 승인 기능 자체가 없다) 감점
 * 대신 상한을 높게 잡는 쪽으로 정했다. {@link com.freepets.domain.gamification.entity.LevelTier}가
 * 7색 × 2바퀴(개→고양이)의 14단계 배지라 5의 배수(14×5=70)로 딱 나눠떨어지게 잡았다 — 레벨
 * 70 도달에 누적 241,500XP가 필요하다(도메인별 지급량 표 기준 하루 최대치로도 몇백 일이
 * 걸리는 수준).
 *
 * <p>두 상수(XP_PER_LEVEL_STEP, MAX_LEVEL) 모두 기획팀이 방향만 정하고 정확한 값은 엔지니어링
 * 제안이라 여기 한 곳만 바꾸면 곡선 전체가 조정된다.
 */
public final class LevelCurve {

    public static final int MAX_LEVEL = 70;

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
     * 오르지 않는다(negative/overflow 걱정 없는 선형 탐색, 최대 70번 반복이라 성능상 문제 없음). */
    public static int levelForTotalXp(long totalXp) {
        int level = 1;
        while (level < MAX_LEVEL && totalXp >= xpToReachLevel(level + 1)) {
            level++;
        }
        return level;
    }

}
