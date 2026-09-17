package com.freepets.domain.gamification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.gamification.dto.GamificationResponseDTO;
import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.gamification.repository.UserBadgeRepository;
import com.freepets.domain.gamification.repository.XpEventRepository;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class GamificationQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserBadgeRepository userBadgeRepository;

    @Mock
    private XpEventRepository xpEventRepository;

    @Mock
    private ReviewRepository reviewRepository;

    private GamificationQueryService gamificationQueryService;

    private void setUpService() {
        gamificationQueryService = new GamificationQueryService(
                userRepository, userBadgeRepository, xpEventRepository, reviewRepository
        );
    }

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

    @Test
    void XpEvent_기반_패밀리는_XpEventRepository_카운트를_그대로_쓴다() {
        setUpService();
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBadgeRepository.findAllByUser_Id(1L)).thenReturn(List.of());
        // 다른 5개 XpEvent 기반 패밀리도 같은 메소드로 조회되니, REVIEW 외에는 전부 0으로 잡아둔다.
        when(xpEventRepository.countByUser_IdAndSourceType(eq(1L), any())).thenReturn(0L);
        when(xpEventRepository.countByUser_IdAndSourceType(1L, XpSourceType.REVIEW)).thenReturn(12L);

        GamificationResponseDTO.MyStatus status = gamificationQueryService.getMyStatus(1L);

        GamificationResponseDTO.BadgeProgress review = status.progress().stream()
                .filter(progress -> progress.family().equals("REVIEW"))
                .findFirst()
                .orElseThrow();
        assertThat(review.count()).isEqualTo(12L);
    }

    @Test
    void HELPFUL_패밀리는_XpEvent_대신_리뷰_도움됐어요_합계를_쓴다() {
        setUpService();
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBadgeRepository.findAllByUser_Id(1L)).thenReturn(List.of());
        when(reviewRepository.sumHelpfulCountByUserId(1L)).thenReturn(30L);

        GamificationResponseDTO.MyStatus status = gamificationQueryService.getMyStatus(1L);

        GamificationResponseDTO.BadgeProgress helpful = status.progress().stream()
                .filter(progress -> progress.family().equals("HELPFUL"))
                .findFirst()
                .orElseThrow();
        assertThat(helpful.count()).isEqualTo(30L);
    }

    @Test
    void 존재하지_않는_유저면_예외를_던진다() {
        setUpService();
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gamificationQueryService.getMyStatus(1L))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorStatus.MEMBER4005);
    }
}
