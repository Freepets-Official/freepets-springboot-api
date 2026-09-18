package com.freepets.domain.gamification.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

    // 서버 프로세스는 항상 UTC로 고정돼 있다(FreepetsServerApplication의 static 블록) — "하루"의
    // 경계는 서버 타임존이 아니라 실제 사용자가 있는 KST 기준이어야 한다. GamificationService의
    // 하루 상한 계산과 정확히 같은 기준이어야 퀘스트 진행률이 실제 지급 여부와 어긋나지 않는다.
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final XpEventRepository xpEventRepository;

    // HELPFUL 패밀리(도움됐어요 총합)만 XpEvent가 안 생기는 행동이라 리뷰 도메인의 합계를
    // 그대로 읽어야 한다 — 리뷰 도메인의 리포지토리를 직접 참조하지 않고, 그 도메인이 소유한
    // 조회 서비스(ReviewQueryService.getTotalHelpfulReceived)를 통해서만 가져온다. 의존
    // 방향이 "게이미피케이션 → 리뷰의 서비스"로만 흐르게 해서, 리포지토리까지 두 도메인이
    // 서로 직접 건드리는 걸 막는다.
    private final ReviewQueryService reviewQueryService;

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
            XpSourceType sourceType = family.getRelatedSourceType();
            long count = sourceType != null
                    ? xpEventCounts.getOrDefault(sourceType, 0L)
                    : reviewQueryService.getTotalHelpfulReceived(userId);
            counts.put(family, count);
        }
        return counts;
    }

    // GET /api/v1/me/gamification/quests — 오늘(KST) 기준 6개 퀘스트 진행률. target은
    // XpSourceType.getDailyCap()과 같은 값이라, completed는 실제 지급 상한과 같은 기준으로
    // 세므로 target을 넘어오지 않는다.
    public GamificationResponseDTO.QuestList getTodayQuests(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new GeneralException(ErrorStatus.MEMBER4005);
        }

        LocalDateTime startOfToday = startOfTodayInBusinessZone();
        List<XpEventRepository.SourceTypeDailyStats> todayStats =
                xpEventRepository.countAndSumGroupedByUser_IdSince(userId, startOfToday);

        return GamificationConverter.toQuestList(startOfToday.plusDays(1), todayStats);
    }

    /**
     * "오늘 자정"을 KST 기준으로 계산해, {@code createdAt}(서버가 항상 UTC로 고정해 저장하는
     * naive LocalDateTime)과 같은 좌표로 맞춰 돌려준다. GamificationService의 동명 메소드와
     * 완전히 같은 계산이다 — 하루 상한 판단(지급 시점)과 퀘스트 진행률(조회 시점)이 정확히
     * 같은 "오늘"을 봐야 하기 때문이다.
     */
    private LocalDateTime startOfTodayInBusinessZone() {
        return LocalDate.now(BUSINESS_ZONE)
                .atStartOfDay(BUSINESS_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
