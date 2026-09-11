package com.freepets.domain.gamification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.gamification.entity.XpEvent;
import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.gamification.repository.XpEventRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class GamificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private XpEventRepository xpEventRepository;

    @Mock
    private GamificationNotificationService gamificationNotificationService;

    @Mock
    private BadgeEvaluationService badgeEvaluationService;

    private GamificationService gamificationService;

    private User newUser() {
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
        gamificationService = new GamificationService(
                userRepository, xpEventRepository, gamificationNotificationService, badgeEvaluationService
        );
    }

    @Test
    void 정상_지급하면_XP가_증가하고_레벨이_갱신되며_레벨업_알림과_배지_재평가가_호출된다() {
        setUpService();
        User user = newUser(); // totalXp=0, level=1

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.REVIEW, 100L))
                .thenReturn(false);

        // 레벨 2에 필요한 누적 XP는 100 — 150을 주면 레벨 2로 올라가야 한다.
        gamificationService.grantXp(1L, XpSourceType.REVIEW, 100L, 150);

        assertThat(user.getTotalXp()).isEqualTo(150);
        assertThat(user.getLevel()).isEqualTo(2);
        verify(xpEventRepository).save(any(XpEvent.class));
        verify(gamificationNotificationService).notifyLevelUp(1L, 2);
        verify(badgeEvaluationService).evaluateAfterXpEvent(user, XpSourceType.REVIEW);
    }

    @Test
    void 레벨업이_아니면_레벨업_알림을_보내지_않는다() {
        setUpService();
        User user = newUser();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.REVIEW, 100L))
                .thenReturn(false);

        gamificationService.grantXp(1L, XpSourceType.REVIEW, 100L, 20);

        assertThat(user.getLevel()).isEqualTo(1);
        verify(gamificationNotificationService, never()).notifyLevelUp(any(), anyInt());
        verify(badgeEvaluationService).evaluateAfterXpEvent(user, XpSourceType.REVIEW);
    }

    @Test
    void 레벨업_알림이_꺼져있으면_레벨업해도_알림을_보내지_않는다() {
        setUpService();
        User user = newUser();
        user.toggleLevelUpNotification(false);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.REVIEW, 100L))
                .thenReturn(false);

        gamificationService.grantXp(1L, XpSourceType.REVIEW, 100L, 150);

        assertThat(user.getLevel()).isEqualTo(2);
        verify(gamificationNotificationService, never()).notifyLevelUp(any(), anyInt());
    }

    @Test
    void 검사와_저장_사이_레이스로_유니크_제약_위반이_나면_조용히_스킵한다() {
        setUpService();
        User user = newUser();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.REVIEW, 100L))
                .thenReturn(false);
        when(xpEventRepository.save(any(XpEvent.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_xp_events_user_source"));

        gamificationService.grantXp(1L, XpSourceType.REVIEW, 100L, 150);

        // uk_xp_events_user_source 위반은 동시에 들어온 같은 지급이 이미 반영됐다는 뜻이라,
        // 이번 호출에서는 유저 상태를 건드리지 않고 조용히 끝나야 한다.
        assertThat(user.getTotalXp()).isZero();
        assertThat(user.getLevel()).isEqualTo(1);
        verifyNoInteractions(gamificationNotificationService);
        verifyNoInteractions(badgeEvaluationService);
    }

    @Test
    void 하루_상한에_도달하면_지급하지_않는다() {
        setUpService();
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.PETCHECK, 555L))
                .thenReturn(false);
        when(xpEventRepository.countByUser_IdAndSourceTypeAndCreatedAtGreaterThanEqual(
                eq(1L), eq(XpSourceType.PETCHECK), any(LocalDateTime.class)
        )).thenReturn(10L); // PETCHECK 하루 상한(10) 도달

        gamificationService.grantXp(1L, XpSourceType.PETCHECK, 555L, 5);

        verifyNoInteractions(userRepository);
        verify(xpEventRepository, never()).save(any());
        verifyNoInteractions(gamificationNotificationService);
        verifyNoInteractions(badgeEvaluationService);
    }

    @Test
    void 코스_공개도_하루_상한이_있다() {
        // sourceId(courseId)가 매번 달라 평생 1회 검사는 항상 통과하므로, 같은 시설을 재사용한
        // 트리비얼한 코스를 계속 새로 만들어 공개해도 하루 상한으로 막혀야 한다.
        setUpService();
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.COURSE_PUBLISHED, 999L))
                .thenReturn(false);
        when(xpEventRepository.countByUser_IdAndSourceTypeAndCreatedAtGreaterThanEqual(
                eq(1L), eq(XpSourceType.COURSE_PUBLISHED), any(LocalDateTime.class)
        )).thenReturn(5L); // COURSE_PUBLISHED 하루 상한(5) 도달

        gamificationService.grantXp(1L, XpSourceType.COURSE_PUBLISHED, 999L, 25);

        verifyNoInteractions(userRepository);
        verify(xpEventRepository, never()).save(any());
    }

    @Test
    void 이미_지급된_sourceId면_스킵한다() {
        setUpService();
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.COURSE_PUBLISHED, 10L))
                .thenReturn(true);

        gamificationService.grantXp(1L, XpSourceType.COURSE_PUBLISHED, 10L, 30);

        verifyNoInteractions(userRepository);
        verify(xpEventRepository, never()).save(any());
        verify(xpEventRepository, never())
                .countByUser_IdAndSourceTypeAndCreatedAtGreaterThanEqual(any(), any(), any());
    }

    @Test
    void 알림_설정을_토글한다() {
        setUpService();
        User user = newUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        boolean result = gamificationService.updateLevelUpNotification(1L, false);

        assertThat(result).isFalse();
        assertThat(user.isLevelUpNotificationEnabled()).isFalse();
    }

    @Test
    void 존재하지_않는_유저의_알림_설정을_토글하면_예외를_던진다() {
        setUpService();
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gamificationService.updateLevelUpNotification(1L, false))
                .isInstanceOf(GeneralException.class);
    }
}
