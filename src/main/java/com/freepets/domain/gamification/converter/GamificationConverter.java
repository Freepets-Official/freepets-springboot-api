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
import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.gamification.repository.XpEventRepository;
import com.freepets.domain.gamification.service.LevelCurve;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;

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
                tier.finish(),
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

    // XpSourceType.values() 선언 순서(판별→리뷰→제보→만족도→코스공개→코스공유) 그대로 6개를
    // 다 내려준다 — 오늘 한 번도 지급받지 않은 sourceType은 todayStats에 행 자체가 없으므로
    // completed/earnedXpToday를 0으로 채운다.
    public static GamificationResponseDTO.QuestList toQuestList(
            LocalDateTime resetsAt,
            List<XpEventRepository.SourceTypeDailyStats> todayStats
    ) {
        Map<XpSourceType, XpEventRepository.SourceTypeDailyStats> statsByType = new HashMap<>();
        for (XpEventRepository.SourceTypeDailyStats stats : todayStats) {
            statsByType.put(stats.getSourceType(), stats);
        }

        List<GamificationResponseDTO.Quest> quests = Arrays.stream(XpSourceType.values())
                .map(sourceType -> toQuest(sourceType, statsByType.get(sourceType)))
                .toList();

        return new GamificationResponseDTO.QuestList(resetsAt, quests);
    }

    private static GamificationResponseDTO.Quest toQuest(
            XpSourceType sourceType,
            XpEventRepository.SourceTypeDailyStats stats
    ) {
        long completed = stats != null ? stats.getCount() : 0L;
        long earnedXpToday = stats != null ? stats.getTotalAmount() : 0L;

        return new GamificationResponseDTO.Quest(
                sourceType, sourceType.getLabel(), completed, sourceType.getDailyCap(), earnedXpToday
        );
    }

    public static GamificationResponseDTO.RankingItem toRankingItem(
            UserRepository.RankingRow row,
            Long viewerId
    ) {
        LevelTier tier = LevelTier.of(row.getLevel());

        return new GamificationResponseDTO.RankingItem(
                row.getRnk(),
                row.getId(),
                row.getNickname(),
                row.getTotalXp(),
                row.getLevel(),
                tier.animal(),
                tier.finish(),
                tier.color(),
                row.getId().equals(viewerId)
        );
    }

    // ranked=false면 rank는 null로 둬 응답에서 키 자체가 빠지게 한다(RankingResult.MyRanking
    // 참고) — 순위를 아예 숨겨야 하는 상황이라 0이나 -1 같은 placeholder를 대신 넣지 않는다.
    public static GamificationResponseDTO.MyRanking toMyRanking(
            User user,
            Long rank,
            long participantCount,
            boolean ranked
    ) {
        LevelTier tier = LevelTier.of(user.getLevel());

        return new GamificationResponseDTO.MyRanking(
                ranked ? rank : null,
                participantCount,
                user.getTotalXp(),
                user.getLevel(),
                tier.animal(),
                tier.finish(),
                tier.color(),
                ranked
        );
    }

}
