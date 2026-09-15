package com.freepets.domain.gamification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void 리뷰_10개를_달성하면_해당하는_단계까지_전부_부여된다() {
        setUpService();
        User user = user();
        // 동(1) 단계는 이미 보유 중이라고 가정 — 은(5)·금(10)만 새로 부여돼야 한다.
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.REVIEW_BRONZE)).thenReturn(true);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.REVIEW_SILVER)).thenReturn(false);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.REVIEW_GOLD)).thenReturn(false);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.REVIEW_RUBY)).thenReturn(false);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.REVIEW_CRYSTAL)).thenReturn(false);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.REVIEW_DIAMOND)).thenReturn(false);
        when(xpEventRepository.countByUser_IdAndSourceType(1L, XpSourceType.REVIEW)).thenReturn(10L);

        badgeEvaluationService.evaluateAfterXpEvent(user, XpSourceType.REVIEW);

        verify(userBadgeRepository, never()).save(argThatBadgeIs(Badge.REVIEW_BRONZE));
        verify(userBadgeRepository).save(argThatBadgeIs(Badge.REVIEW_SILVER));
        verify(userBadgeRepository).save(argThatBadgeIs(Badge.REVIEW_GOLD));
        verify(userBadgeRepository, never()).save(argThatBadgeIs(Badge.REVIEW_RUBY));
        verify(gamificationNotificationService).notifyBadgeEarned(1L, Badge.REVIEW_SILVER);
        verify(gamificationNotificationService).notifyBadgeEarned(1L, Badge.REVIEW_GOLD);
        verify(gamificationNotificationService, never()).notifyBadgeEarned(1L, Badge.REVIEW_BRONZE);
    }

    @Test
    void 이미_보유한_배지는_다시_부여하지_않는다() {
        setUpService();
        User user = user();
        // PETCHECK 관련 배지(동/은/금/루비/크리스탈/다이아) 전부 이미 가진 것으로 —
        // 이 테스트가 확인하려는 건 "이미 있으면 개수 조회 자체를 안 한다"는 것뿐이다.
        when(userBadgeRepository.existsByUser_IdAndBadge(eq(1L), any())).thenReturn(true);

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
        when(userBadgeRepository.existsByUser_IdAndBadge(eq(1L), any())).thenReturn(false);
        when(xpEventRepository.countByUser_IdAndSourceType(1L, XpSourceType.REVIEW)).thenReturn(0L);

        badgeEvaluationService.evaluateAfterXpEvent(user, XpSourceType.REVIEW);

        verify(userBadgeRepository, never()).save(any());
        verify(gamificationNotificationService, never()).notifyBadgeEarned(any(), any());
    }

    @Test
    void 관련없는_sourceType의_배지는_확인하지_않는다() {
        setUpService();
        User user = user();
        // 이미 다 가진 것으로 응답해 count 조회로까지 새지 않게 한다 — 이 테스트가 확인하려는
        // 건 "관련 없는 sourceType의 배지는 아예 안 건드린다"는 것뿐이다.
        when(userBadgeRepository.existsByUser_IdAndBadge(eq(1L), any())).thenReturn(true);

        badgeEvaluationService.evaluateAfterXpEvent(user, XpSourceType.PETCHECK);

        verify(userBadgeRepository, never()).existsByUser_IdAndBadge(1L, Badge.REVIEW_BRONZE);
        verify(userBadgeRepository, never()).existsByUser_IdAndBadge(1L, Badge.REVIEW_GOLD);
        verify(xpEventRepository, never()).countByUser_IdAndSourceType(1L, XpSourceType.REVIEW);
    }

    @Test
    void 도움됐어요_총합이_금_단계_기준_이상이면_구원자_배지가_부여된다() {
        setUpService();
        User user = user();
        // 하위 단계(동/은)는 이미 보유 중이라고 가정 — 금(10) 단계만 새로 부여돼야 한다.
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.HELPFUL_BRONZE)).thenReturn(true);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.HELPFUL_SILVER)).thenReturn(true);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.HELPFUL_GOLD)).thenReturn(false);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.HELPFUL_RUBY)).thenReturn(false);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.HELPFUL_CRYSTAL)).thenReturn(false);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.HELPFUL_DIAMOND)).thenReturn(false);

        badgeEvaluationService.evaluateHelpfulSaviorBadge(user, 10L);

        verify(userBadgeRepository).save(argThatBadgeIs(Badge.HELPFUL_GOLD));
        verify(gamificationNotificationService).notifyBadgeEarned(1L, Badge.HELPFUL_GOLD);
        verify(userBadgeRepository, never()).save(argThatBadgeIs(Badge.HELPFUL_RUBY));
    }

    @Test
    void 도움됐어요_총합이_기준에_못_미치면_구원자_배지를_주지_않는다() {
        setUpService();
        User user = user();
        // 하위 단계(동/은)는 이미 보유 중이라고 가정 — 금(10)은 아직 총합(9)이 못 미친다.
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.HELPFUL_BRONZE)).thenReturn(true);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.HELPFUL_SILVER)).thenReturn(true);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.HELPFUL_GOLD)).thenReturn(false);

        badgeEvaluationService.evaluateHelpfulSaviorBadge(user, 9L);

        verify(userBadgeRepository, never()).save(any());
        verify(gamificationNotificationService, never()).notifyBadgeEarned(any(), any());
    }

    @Test
    void 이미_구원자_배지가_있으면_다시_부여하지_않는다() {
        setUpService();
        User user = user();
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.HELPFUL_BRONZE)).thenReturn(true);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.HELPFUL_SILVER)).thenReturn(true);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.HELPFUL_GOLD)).thenReturn(true);
        // 상위 단계(루비/크리스탈/다이아)는 아직 기준(50/100/500) 미달이라 이 값(10)으로는
        // 같이 확인돼도 부여되지 않는다.
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.HELPFUL_RUBY)).thenReturn(false);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.HELPFUL_CRYSTAL)).thenReturn(false);
        when(userBadgeRepository.existsByUser_IdAndBadge(1L, Badge.HELPFUL_DIAMOND)).thenReturn(false);

        badgeEvaluationService.evaluateHelpfulSaviorBadge(user, 10L);

        verify(userBadgeRepository, never()).save(any());
        verify(gamificationNotificationService, never()).notifyBadgeEarned(any(), any());
    }

    @Test
    void 총합이_한번에_크게_뛰면_해당하는_단계를_전부_부여한다() {
        setUpService();
        User user = user();
        when(userBadgeRepository.existsByUser_IdAndBadge(eq(1L), any())).thenReturn(false);

        // 여러 리뷰가 한꺼번에 몰려 도움됐어요를 받아 총합이 60이 됐다고 가정 — 동(1)·은(5)·
        // 금(10)·루비(50) 단계는 이미 기준을 넘었고 크리스탈(100)·다이아(500)는 아직이다.
        badgeEvaluationService.evaluateHelpfulSaviorBadge(user, 60L);

        verify(userBadgeRepository).save(argThatBadgeIs(Badge.HELPFUL_BRONZE));
        verify(userBadgeRepository).save(argThatBadgeIs(Badge.HELPFUL_SILVER));
        verify(userBadgeRepository).save(argThatBadgeIs(Badge.HELPFUL_GOLD));
        verify(userBadgeRepository).save(argThatBadgeIs(Badge.HELPFUL_RUBY));
        verify(userBadgeRepository, never()).save(argThatBadgeIs(Badge.HELPFUL_CRYSTAL));
        verify(userBadgeRepository, never()).save(argThatBadgeIs(Badge.HELPFUL_DIAMOND));
    }

    private UserBadge argThatBadgeIs(Badge badge) {
        return org.mockito.ArgumentMatchers.argThat(userBadge -> userBadge != null && userBadge.getBadge() == badge);
    }
}
