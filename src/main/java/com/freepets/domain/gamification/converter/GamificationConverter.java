package com.freepets.domain.gamification.converter;

import java.util.List;

import com.freepets.domain.gamification.dto.GamificationResponseDTO;
import com.freepets.domain.gamification.entity.LevelTier;
import com.freepets.domain.gamification.entity.UserBadge;
import com.freepets.domain.gamification.service.LevelCurve;
import com.freepets.domain.user.entity.User;

public class GamificationConverter {

    private GamificationConverter() {}

    public static GamificationResponseDTO.MyStatus toMyStatus(
            User user,
            List<UserBadge> badges
    ) {
        int level = user.getLevel();
        LevelTier tier = LevelTier.of(level);
        Long xpToNextLevel = level < LevelCurve.MAX_LEVEL
                ? LevelCurve.xpToReachLevel(level + 1) - user.getTotalXp()
                : null;

        return new GamificationResponseDTO.MyStatus(
                level,
                user.getTotalXp(),
                xpToNextLevel,
                tier.animal(),
                tier.color(),
                tier.label(),
                tier.badgeImageUrl(),
                user.isLevelUpNotificationEnabled(),
                badges.stream().map(GamificationConverter::toBadgeSummary).toList()
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

}
