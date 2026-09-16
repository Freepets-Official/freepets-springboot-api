package com.freepets.domain.gamification.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.gamification.dto.GamificationResponseDTO;
import com.freepets.domain.gamification.entity.Badge;
import com.freepets.domain.gamification.entity.BadgeFamily;
import com.freepets.domain.gamification.entity.PawAnimal;
import com.freepets.domain.gamification.entity.PawColor;
import com.freepets.domain.gamification.entity.PawFinish;
import com.freepets.domain.gamification.entity.UserBadge;
import com.freepets.domain.gamification.service.LevelCurve;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

class GamificationConverterTest {

    private User user(
            long totalXp,
            int level
    ) {
        User user = User.builder()
                .email("test@freepets.com")
                .passwordHash("hash")
                .nickname("테스터")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "totalXp", totalXp);
        ReflectionTestUtils.setField(user, "level", level);
        return user;
    }

    @Test
    void 레벨_2에서_다음_레벨까지_남은_XP를_계산한다() {
        // 레벨 3에 필요한 누적 XP는 300 — 지금 150이면 150이 남아야 한다.
        User user = user(150, 2);

        GamificationResponseDTO.MyStatus status = GamificationConverter.toMyStatus(user, List.of(), Map.of());

        assertThat(status.xpToNextLevel()).isEqualTo(LevelCurve.xpToReachLevel(3) - 150);
        // 레벨 1=개·흐릿함·빨강, 레벨 2=개·흐릿함·주황(레벨마다 한 칸씩 색이 바뀐다).
        assertThat(status.tierAnimal()).isEqualTo(PawAnimal.DOG);
        assertThat(status.tierFinish()).isEqualTo(PawFinish.DIM);
        assertThat(status.tierColor()).isEqualTo(PawColor.ORANGE);
        assertThat(status.tierLabel()).isEqualTo("개 발바닥 · 흐릿함 · 주황");
    }

    @Test
    void 최대_레벨이면_xpToNextLevel이_null이다() {
        User user = user(LevelCurve.xpToReachLevel(LevelCurve.MAX_LEVEL), LevelCurve.MAX_LEVEL);

        GamificationResponseDTO.MyStatus status = GamificationConverter.toMyStatus(user, List.of(), Map.of());

        assertThat(status.xpToNextLevel()).isNull();
        // 레벨 70 = 두 번째 동물(고양이)의 마지막 단계(홀로그램)·마지막 색(보라) —
        // 2종×5단계×7색이 정확히 70에서 끝난다.
        assertThat(status.tierAnimal()).isEqualTo(PawAnimal.CAT);
        assertThat(status.tierFinish()).isEqualTo(PawFinish.HOLOGRAPHIC);
        assertThat(status.tierColor()).isEqualTo(PawColor.VIOLET);
        assertThat(status.tierLabel()).isEqualTo("고양이 발바닥 · 홀로그램 · 보라");
    }

    @Test
    void 보유한_배지_목록이_그대로_담긴다() {
        User user = user(0, 1);
        UserBadge userBadge = UserBadge.builder().user(user).badge(Badge.REVIEW_BRONZE).build();

        GamificationResponseDTO.MyStatus status = GamificationConverter.toMyStatus(user, List.of(userBadge), Map.of());

        assertThat(status.badges()).hasSize(1);
        assertThat(status.badges().get(0).code()).isEqualTo("REVIEW_BRONZE");
        assertThat(status.badges().get(0).label()).isEqualTo(Badge.REVIEW_BRONZE.getLabel());
    }

    @Test
    void 진행도는_패밀리마다_하나씩_7개가_담긴다() {
        User user = user(0, 1);

        GamificationResponseDTO.MyStatus status = GamificationConverter.toMyStatus(user, List.of(), Map.of());

        assertThat(status.progress()).hasSize(BadgeFamily.values().length);
        assertThat(status.progress()).extracting(GamificationResponseDTO.BadgeProgress::family)
                .containsExactlyInAnyOrder(
                        "PETCHECK", "REVIEW", "REPORT", "SATISFACTION",
                        "COURSE_PUBLISHED", "COURSE_SHARED", "HELPFUL"
                );
    }

    @Test
    void 진행도의_단계는_동에서_다이아까지_임계값_오름차순이다() {
        User user = user(0, 1);

        GamificationResponseDTO.MyStatus status = GamificationConverter.toMyStatus(user, List.of(), Map.of());

        GamificationResponseDTO.BadgeProgress review = status.progress().stream()
                .filter(progress -> progress.family().equals("REVIEW"))
                .findFirst()
                .orElseThrow();

        assertThat(review.tiers()).extracting(GamificationResponseDTO.TierProgress::tier)
                .containsExactly("BRONZE", "SILVER", "GOLD", "RUBY", "CRYSTAL", "DIAMOND");
        assertThat(review.tiers()).extracting(GamificationResponseDTO.TierProgress::threshold)
                .containsExactly(1, 5, 10, 50, 100, 500);
    }

    @Test
    void 진행도의_count는_전달받은_패밀리별_누적치를_그대로_쓴다() {
        User user = user(0, 1);

        GamificationResponseDTO.MyStatus status = GamificationConverter.toMyStatus(
                user, List.of(), Map.of(BadgeFamily.REVIEW, 12L)
        );

        GamificationResponseDTO.BadgeProgress review = status.progress().stream()
                .filter(progress -> progress.family().equals("REVIEW"))
                .findFirst()
                .orElseThrow();
        assertThat(review.count()).isEqualTo(12L);

        // 전달받지 못한 패밀리는 0으로 취급한다.
        GamificationResponseDTO.BadgeProgress petcheck = status.progress().stream()
                .filter(progress -> progress.family().equals("PETCHECK"))
                .findFirst()
                .orElseThrow();
        assertThat(petcheck.count()).isEqualTo(0L);
    }

    @Test
    void 이미_획득한_단계는_획득_시각이_담기고_아직이면_null이다() {
        User user = user(0, 1);
        UserBadge earned = UserBadge.builder().user(user).badge(Badge.REVIEW_BRONZE).build();
        java.time.LocalDateTime earnedAt = java.time.LocalDateTime.of(2026, 9, 1, 0, 0);
        ReflectionTestUtils.setField(earned, "createdAt", earnedAt);

        GamificationResponseDTO.MyStatus status = GamificationConverter.toMyStatus(user, List.of(earned), Map.of());

        GamificationResponseDTO.BadgeProgress review = status.progress().stream()
                .filter(progress -> progress.family().equals("REVIEW"))
                .findFirst()
                .orElseThrow();

        GamificationResponseDTO.TierProgress bronze = review.tiers().stream()
                .filter(tier -> tier.tier().equals("BRONZE"))
                .findFirst()
                .orElseThrow();
        GamificationResponseDTO.TierProgress silver = review.tiers().stream()
                .filter(tier -> tier.tier().equals("SILVER"))
                .findFirst()
                .orElseThrow();

        assertThat(bronze.earnedAt()).isEqualTo(earnedAt);
        assertThat(silver.earnedAt()).isNull();
    }
}
