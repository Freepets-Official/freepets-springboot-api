package com.freepets.domain.gamification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.gamification.entity.Badge;
import com.freepets.domain.gamification.entity.UserBadge;
import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.gamification.repository.UserBadgeRepository;
import com.freepets.domain.gamification.repository.XpEventRepository;
import com.freepets.domain.user.entity.User;

import lombok.RequiredArgsConstructor;

// XpEvent가 하나 저장된 직후 GamificationService가 호출한다 — 매번 전체 배지 카탈로그를 다
// 훑지 않고, 방금 생긴 이벤트의 sourceType과 관련된 배지만 골라 확인한다(Badge.relatedSourceType).
@Service
@RequiredArgsConstructor
@Transactional
public class BadgeEvaluationService {

    private final UserBadgeRepository userBadgeRepository;
    private final XpEventRepository xpEventRepository;
    private final GamificationNotificationService gamificationNotificationService;

    public void evaluateAfterXpEvent(
            User user,
            XpSourceType sourceType
    ) {
        for (Badge badge : Badge.values()) {
            if (badge.getRelatedSourceType() != sourceType) {
                continue;
            }
            evaluateXpBadge(user, badge, sourceType);
        }
    }

    // 이미 보유 중이면 개수 조회 자체를 안 하도록, exists 확인을 count 쿼리보다 먼저 한다 —
    // XpEvent 개수 집계가 매번 값싼 연산은 아니라서다.
    private void evaluateXpBadge(
            User user,
            Badge badge,
            XpSourceType sourceType
    ) {
        if (userBadgeRepository.existsByUser_IdAndBadge(user.getId(), badge)) {
            return;
        }

        long achievedCount = xpEventRepository.countByUser_IdAndSourceType(user.getId(), sourceType);
        award(user, badge, achievedCount);
    }

    /**
     * "구원자"(HELPFUL_10) 평가 — 본인 행동(XpEvent)이 아니라 남이 내 리뷰를 "도움됐어요"로
     * 표시하는 게 트리거라 {@link #evaluateAfterXpEvent}의 XpEvent 카운트 방식을 못 쓴다.
     * 리뷰 도메인이 이미 계산해 온 총합(review 도메인 소유 개념 — 이 서비스가 ReviewRepository를
     * 직접 참조하지 않는다)을 그대로 받아 임계값만 비교한다.
     */
    public void evaluateHelpfulSaviorBadge(
            User reviewAuthor,
            long totalHelpfulReceived
    ) {
        if (userBadgeRepository.existsByUser_IdAndBadge(reviewAuthor.getId(), Badge.HELPFUL_10)) {
            return;
        }

        award(reviewAuthor, Badge.HELPFUL_10, totalHelpfulReceived);
    }

    private void award(
            User user,
            Badge badge,
            long achievedCount
    ) {
        if (achievedCount < badge.getThreshold()) {
            return;
        }

        userBadgeRepository.save(
                UserBadge.builder()
                        .user(user)
                        .badge(badge)
                        .build()
        );
        gamificationNotificationService.notifyBadgeEarned(user.getId(), badge);
    }
}
