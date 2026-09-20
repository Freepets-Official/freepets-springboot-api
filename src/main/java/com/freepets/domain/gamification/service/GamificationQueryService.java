package com.freepets.domain.gamification.service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.gamification.converter.GamificationConverter;
import com.freepets.domain.gamification.dto.GamificationResponseDTO;
import com.freepets.domain.gamification.entity.BadgeFamily;
import com.freepets.domain.gamification.entity.UserBadge;
import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.gamification.repository.UserBadgeRepository;
import com.freepets.domain.gamification.repository.XpEventRepository;
import com.freepets.domain.review.service.ReviewQueryService;
import com.freepets.domain.stamp.service.StampQueryService;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

// GET /api/v1/me/gamification — 레벨·XP·티어·배지·패밀리별 진행도 조회.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GamificationQueryService {

    private final UserRepository userRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final XpEventRepository xpEventRepository;

    // HELPFUL 패밀리(도움됐어요 총합)만 XpEvent가 안 생기는 행동이라 리뷰 도메인의 합계를
    // 그대로 읽어야 한다 — 리뷰 도메인의 리포지토리를 직접 참조하지 않고, 그 도메인이 소유한
    // 조회 서비스(ReviewQueryService.getTotalHelpfulReceived)를 통해서만 가져온다. 의존
    // 방향이 "게이미피케이션 → 리뷰의 서비스"로만 흐르게 해서, 리포지토리까지 두 도메인이
    // 서로 직접 건드리는 걸 막는다.
    private final ReviewQueryService reviewQueryService;

    // STAMP·REGION 패밀리(도장 수·distinct 지역 수)도 같은 이유로 stamp 도메인의 조회 서비스를
    // 통해서만 가져온다 — GET /me/stamps의 summary.total/summary.regionCount와 계산이 갈리면
    // 도장첩 요약과 배지 진행도가 서로 다른 숫자를 보여주게 된다.
    private final StampQueryService stampQueryService;

    public GamificationResponseDTO.MyStatus getMyStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        List<UserBadge> badges = userBadgeRepository.findAllByUser_Id(userId);
        Map<BadgeFamily, Long> familyCounts = countByFamily(userId);

        return GamificationConverter.toMyStatus(user, badges, familyCounts);
    }

    // sourceType이 있는 6개 패밀리는 그룹 쿼리 하나로 한 번에 가져온다 — 패밀리 수만큼
    // countByUser_IdAndSourceType을 반복 호출하지 않는다(패밀리가 7개면 count 쿼리 6번이 매
    // 조회마다 나가던 것 — 이 화면은 앱 진입 때마다 불릴 수 있는 곳이라 그 차이가 크다).
    // HELPFUL은 XpEvent 기반이 아니라 이 그룹 쿼리에 안 잡히므로 별도로 채운다.
    private Map<BadgeFamily, Long> countByFamily(Long userId) {
        Map<XpSourceType, Long> xpEventCounts = new EnumMap<>(XpSourceType.class);
        for (XpEventRepository.SourceTypeCount row : xpEventRepository.countGroupedByUser_Id(userId)) {
            xpEventCounts.put(row.getSourceType(), row.getCount());
        }

        Map<BadgeFamily, Long> counts = new EnumMap<>(BadgeFamily.class);
        for (BadgeFamily family : BadgeFamily.values()) {
            counts.put(family, countOf(family, userId, xpEventCounts));
        }
        return counts;
    }

    // relatedSourceType이 있는 패밀리는 XpEvent 그룹 카운트를 그대로 쓰고, null인 패밀리는
    // 각자 값을 소유한 도메인의 조회 서비스로 분기한다(HELPFUL→리뷰, STAMP·REGION→stamp).
    private long countOf(
            BadgeFamily family,
            Long userId,
            Map<XpSourceType, Long> xpEventCounts
    ) {
        XpSourceType sourceType = family.getRelatedSourceType();
        if (sourceType != null) {
            return xpEventCounts.getOrDefault(sourceType, 0L);
        }

        return switch (family) {
            case HELPFUL -> reviewQueryService.getTotalHelpfulReceived(userId);
            case STAMP -> stampQueryService.getTotalStampCount(userId);
            case REGION -> stampQueryService.getDistinctRegionCount(userId);
            default -> 0L;
        };
    }

    // GET /api/v1/me/gamification/quests — 오늘(KST) 기준 6개 퀘스트 진행률. target은
    // XpSourceType.getDailyCap()과 같은 값이라, completed는 실제 지급 상한과 같은 기준으로
    // 세므로 target을 넘어오지 않는다.
    public GamificationResponseDTO.QuestList getTodayQuests(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new GeneralException(ErrorStatus.MEMBER4005);
        }

        // GamificationService.startOfTodayInBusinessZone()을 그대로 쓴다 — 하루 상한 판단(지급
        // 시점)과 퀘스트 진행률(조회 시점)이 정확히 같은 "오늘" 기준을 봐야 하고, 그 기준을
        // 실제로 상한에 적용하는 클래스가 소유하는 게 맞다(GamificationService 쪽 주석 참고).
        LocalDateTime startOfToday = GamificationService.startOfTodayInBusinessZone();
        List<XpEventRepository.SourceTypeDailyStats> todayStats =
                xpEventRepository.countAndSumGroupedByUser_IdSince(userId, startOfToday);

        return GamificationConverter.toQuestList(startOfToday.plusDays(1), todayStats);
    }
}
