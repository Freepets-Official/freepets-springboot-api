package com.freepets.domain.gamification.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LevelCurveTest {

    @Test
    void 레벨_1은_0XP부터_시작한다() {
        assertThat(LevelCurve.xpToReachLevel(1)).isEqualTo(0);
        assertThat(LevelCurve.levelForTotalXp(0)).isEqualTo(1);
    }

    @Test
    void 레벨이_오를수록_다음_레벨까지_필요한_XP가_더_늘어난다() {
        long stepTo2 = LevelCurve.xpToReachLevel(2) - LevelCurve.xpToReachLevel(1);
        long stepTo3 = LevelCurve.xpToReachLevel(3) - LevelCurve.xpToReachLevel(2);
        long stepTo4 = LevelCurve.xpToReachLevel(4) - LevelCurve.xpToReachLevel(3);

        assertThat(stepTo2).isEqualTo(100);
        assertThat(stepTo3).isEqualTo(200);
        assertThat(stepTo4).isEqualTo(300);
        assertThat(stepTo3).isGreaterThan(stepTo2);
        assertThat(stepTo4).isGreaterThan(stepTo3);
    }

    @Test
    void 레벨_경계값에서_정확히_그_레벨로_계산된다() {
        long xpForLevel5 = LevelCurve.xpToReachLevel(5);

        assertThat(LevelCurve.levelForTotalXp(xpForLevel5)).isEqualTo(5);
        assertThat(LevelCurve.levelForTotalXp(xpForLevel5 - 1)).isEqualTo(4);
        assertThat(LevelCurve.levelForTotalXp(xpForLevel5 + 1)).isEqualTo(5);
    }

    @Test
    void 최대_레벨에_도달하면_더_이상_오르지_않는다() {
        long xpForMaxLevel = LevelCurve.xpToReachLevel(LevelCurve.MAX_LEVEL);

        assertThat(LevelCurve.levelForTotalXp(xpForMaxLevel)).isEqualTo(LevelCurve.MAX_LEVEL);
        assertThat(LevelCurve.levelForTotalXp(xpForMaxLevel + 1_000_000)).isEqualTo(LevelCurve.MAX_LEVEL);
    }

    @Test
    void 최대_레벨을_넘는_레벨을_물으면_최대_레벨_기준값으로_클램핑된다() {
        assertThat(LevelCurve.xpToReachLevel(LevelCurve.MAX_LEVEL + 10))
                .isEqualTo(LevelCurve.xpToReachLevel(LevelCurve.MAX_LEVEL));
    }

    // 레벨 상한이 70에서 40으로 내려오면서(PR #49), 이미 41~70레벨을 찍은 기존 계정의
    // User.level 컬럼이 다음 XP 지급 전까지 그대로 남는다 — 응답을 만들 때 이 값으로 잘라낸다.
    @Test
    void clampLevel은_최대_레벨을_넘는_값을_최대_레벨로_잘라낸다() {
        assertThat(LevelCurve.clampLevel(70)).isEqualTo(LevelCurve.MAX_LEVEL);
        assertThat(LevelCurve.clampLevel(LevelCurve.MAX_LEVEL)).isEqualTo(LevelCurve.MAX_LEVEL);
        assertThat(LevelCurve.clampLevel(1)).isEqualTo(1);
    }
}
