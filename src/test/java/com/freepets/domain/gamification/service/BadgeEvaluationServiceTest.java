package com.freepets.domain.gamification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.gamification.entity.Badge;
import com.freepets.domain.gamification.entity.UserBadge;
import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.gamification.repository.UserBadgeRepository;
import com.freepets.domain.gamification.repository.XpEventRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class BadgeEvaluationServiceTest {

    @Mock
    private UserBadgeRepository userBadgeRepository;

    @Mock
    private XpEventRepository xpEventRepository;

    @Mock
    private GamificationNotificationService gamificationNotificationService;

    private BadgeEvaluationService badgeEvaluationService;

    private User user() {
        User user = User.builder()
                .email("test@freepets.com")
                .passwordHash("hash")
                .nickname("테스터")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private void setUpService() {
        badgeEvaluationService = new BadgeEvaluationService(
                userBadgeRepository, xpEventRepository, gamificationNotificationService
        );
    }

    @Test
    void 리뷰_10개를_달성하면_REVIEWS_10_배지가_부여된다() {
        setUpService();
        User user = user();
        // 첫 리뷰 배지는 이미 보유 중이라고 가정 — REVIEWS_10만 새로 부여돼야 한다.
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.FIRST_REVIEW)).thenReturn(true);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.REVIEWS_10)).thenReturn(false);
        when(xpEventRepository.countByUser_IdAndSourceType(1L, XpSourceType.REVIEW)).thenReturn(10L);

        badgeEvaluationService.evaluateAfterXpEvent(user, XpSourceType.REVIEW);

        verify(userBadgeRepository, never()).save(argThatBadgeIs(Badge.FIRST_REVIEW));
        verify(userBadgeRepository).save(argThatBadgeIs(Badge.REVIEWS_10));
        verify(gamificationNotificationService).notifyBadgeEarned(1L, Badge.REVIEWS_10);
        verify(gamificationNotificationService, never()).notifyBadgeEarned(1L, Badge.FIRST_REVIEW);
    }

    @Test
    void 이미_보유한_배지는_다시_부여하지_않는다() {
        setUpService();
        User user = user();
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.FIRST_PETCHECK)).thenReturn(true);

        badgeEvaluationService.evaluateAfterXpEvent(user, XpSourceType.PETCHECK);

        verify(userBadgeRepository, never()).save(any());
        verify(gamificationNotificationService, never()).notifyBadgeEarned(any(), any());
        // 이미 보유 중이면 개수 조회 자체를 할 필요가 없다.
        verify(xpEventRepository, never()).countByUser_IdAndSourceType(any(), any());
    }

    @Test
    void 아직_기준에_못_미치면_부여하지_않는다() {
        setUpService();
        User user = user();
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.FIRST_REVIEW)).thenReturn(false);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.REVIEWS_10)).thenReturn(false);
        when(xpEventRepository.countByUser_IdAndSourceType(1L, XpSourceType.REVIEW)).thenReturn(0L);

        badgeEvaluationService.evaluateAfterXpEvent(user, XpSourceType.REVIEW);

        verify(userBadgeRepository, never()).save(any());
        verify(gamificationNotificationService, never()).notifyBadgeEarned(any(), any());
    }

    @Test
    void 관련없는_sourceType의_배지는_확인하지_않는다() {
        setUpService();
        User user = user();

        // SATISFACTION은 관련된 배지가 하나도 없다 — existsByUser_IdAndBadge 자체가 호출되면 안 된다.
        badgeEvaluationService.evaluateAfterXpEvent(user, XpSourceType.SATISFACTION);

        verify(userBadgeRepository, never()).existsByUser_IdAndBadge(any(), any());
    }

    private UserBadge argThatBadgeIs(Badge badge) {
        return org.mockito.ArgumentMatchers.argThat(userBadge -> userBadge != null && userBadge.getBadge() == badge);
    }
}
