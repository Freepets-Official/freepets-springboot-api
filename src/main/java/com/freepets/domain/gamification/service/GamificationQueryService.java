package com.freepets.domain.gamification.service;

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
import com.freepets.domain.review.repository.ReviewRepository;
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
    // 그대로 읽어야 한다 — 리뷰 도메인이 소유한 값을 조회 전용으로 가져오는 것뿐이라(쓰기
    // 로직은 여전히 GamificationService.evaluateHelpfulSaviorBadge처럼 호출부가 계산해서
    // 넘기는 방향을 지킨다), 이 화면 전용 조회 하나를 위해 리뷰 도메인에 새 메소드를 얹기보다
    // 이미 있는 집계 쿼리를 바로 재사용한다.
    private final ReviewRepository reviewRepository;

    public GamificationResponseDTO.MyStatus getMyStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        List<UserBadge> badges = userBadgeRepository.findAllByUser_Id(userId);
        Map<BadgeFamily, Long> familyCounts = countByFamily(userId);

        return GamificationConverter.toMyStatus(user, badges, familyCounts);
    }

    private Map<BadgeFamily, Long> countByFamily(Long userId) {
        Map<BadgeFamily, Long> counts = new EnumMap<>(BadgeFamily.class);
        for (BadgeFamily family : BadgeFamily.values()) {
            counts.put(family, countFor(family, userId));
        }
        return counts;
    }

    private long countFor(
            BadgeFamily family,
            Long userId
    ) {
        XpSourceType sourceType = family.getRelatedSourceType();
        if (sourceType != null) {
            return xpEventRepository.countByUser_IdAndSourceType(userId, sourceType);
        }
        return reviewRepository.sumHelpfulCountByUserId(userId);
    }
}
