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
            evaluate(user, badge, sourceType);
        }
    }

    private void evaluate(
            User user,
            Badge badge,
            XpSourceType sourceType
    ) {
        if (userBadgeRepository.existsByUser_IdAndBadge(user.getId(), badge)) {
            return;
        }

        long achievedCount = xpEventRepository.countByUser_IdAndSourceType(user.getId(), sourceType);
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
