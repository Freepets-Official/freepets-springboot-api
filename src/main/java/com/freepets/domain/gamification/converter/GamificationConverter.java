package com.freepets.domain.gamification.converter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.freepets.domain.gamification.dto.GamificationResponseDTO;
import com.freepets.domain.gamification.entity.Badge;
import com.freepets.domain.gamification.entity.BadgeFamily;
import com.freepets.domain.gamification.entity.LevelTier;
import com.freepets.domain.gamification.entity.UserBadge;
import com.freepets.domain.gamification.service.LevelCurve;
import com.freepets.domain.user.entity.User;

public class GamificationConverter {

    private GamificationConverter() {}

    public static GamificationResponseDTO.MyStatus toMyStatus(
            User user,
            List<UserBadge> badges,
            Map<BadgeFamily, Long> familyCounts
    ) {
        int level = user.getLevel();
        LevelTier tier = LevelTier.of(level);
        Long xpToNextLevel = level < LevelCurve.MAX_LEVEL
                ? LevelCurve.xpToReachLevel(level + 1) - user.getTotalXp()
                : null;

        // Collectors.toMap은 값이 null이면(예: 테스트에서 JPA auditing 없이 만든 UserBadge라
        // createdAt이 아직 null) NPE를 던져서 직접 채운다.
        Map<Badge, LocalDateTime> earnedAt = new HashMap<>();
        for (UserBadge userBadge : badges) {
            earnedAt.put(userBadge.getBadge(), userBadge.getCreatedAt());
        }

        return new GamificationResponseDTO.MyStatus(
                level,
                user.getTotalXp(),
                xpToNextLevel,
                tier.animal(),
                tier.color(),
                tier.label(),
                tier.badgeImageUrl(),
                user.isLevelUpNotificationEnabled(),
                badges.stream().map(GamificationConverter::toBadgeSummary).toList(),
                Arrays.stream(BadgeFamily.values())
                        .map(family -> toBadgeProgress(family, familyCounts.getOrDefault(family, 0L), earnedAt))
                        .toList()
        );
    }

    public static GamificationResponseDTO.BadgeSummary toBadgeSummary(UserBadge userBadge) {
        return new GamificationResponseDTO.BadgeSummary(
                userBadge.getBadge().name(),
                userBadge.getBadge().getLabel(),
                userBadge.getBadge().getDescription(),
                userBadge.getCreatedAt()
        );
    }

    // 패밀리 하나(예: 리뷰)의 6단계 진행도. Badge.values()에서 이 패밀리 소속만 골라 threshold
    // 오름차순(동→다이아)으로 정렬한다 — Badge enum 선언 순서에 기대지 않기 위함이다.
    private static GamificationResponseDTO.BadgeProgress toBadgeProgress(
            BadgeFamily family,
            long count,
            Map<Badge, LocalDateTime> earnedAt
    ) {
        List<GamificationResponseDTO.TierProgress> tiers = Arrays.stream(Badge.values())
                .filter(badge -> badge.getFamily() == family)
                .sorted(Comparator.comparingInt(Badge::getThreshold))
                .map(badge -> new GamificationResponseDTO.TierProgress(
                        badge.getTier().name(),
                        badge.getThreshold(),
                        earnedAt.get(badge)
                ))
                .toList();

        return new GamificationResponseDTO.BadgeProgress(family.name(), family.getLabel(), count, tiers);
    }

}
