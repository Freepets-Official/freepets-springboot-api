package com.freepets.domain.gamification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.freepets.domain.review.service.ReviewQueryService;
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
    private ReviewQueryService reviewQueryService;

    private GamificationQueryService gamificationQueryService;

    private void setUpService() {
        gamificationQueryService = new GamificationQueryService(
                userRepository, userBadgeRepository, xpEventRepository, reviewQueryService
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

    private XpEventRepository.SourceTypeCount countRow(
            XpSourceType sourceType,
            long count
    ) {
        return new XpEventRepository.SourceTypeCount() {
            @Override
            public XpSourceType getSourceType() {
                return sourceType;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    @Test
    void XpEvent_기반_패밀리는_그룹_쿼리_한_번으로_전부_채운다() {
        setUpService();
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBadgeRepository.findAllByUser_Id(1L)).thenReturn(List.of());
        // 그룹 쿼리 결과에 REVIEW만 있고 나머지 5개 소스타입은 아예 행이 없다 —
        // 한 번도 지급받은 적 없는 소스타입은 결과에 없는 게 정상이라, 그 경우 0으로 채워야 한다.
        when(xpEventRepository.countGroupedByUser_Id(1L)).thenReturn(List.of(countRow(XpSourceType.REVIEW, 12L)));

        GamificationResponseDTO.MyStatus status = gamificationQueryService.getMyStatus(1L);

        GamificationResponseDTO.BadgeProgress review = status.progress().stream()
                .filter(progress -> progress.family().equals("REVIEW"))
                .findFirst()
                .orElseThrow();
        assertThat(review.count()).isEqualTo(12L);

        GamificationResponseDTO.BadgeProgress petcheck = status.progress().stream()
                .filter(progress -> progress.family().equals("PETCHECK"))
                .findFirst()
                .orElseThrow();
        assertThat(petcheck.count()).isEqualTo(0L);
    }

    @Test
    void XpEvent_카운트는_소스타입별로_반복_조회하지_않고_그룹_쿼리_한_번만_부른다() {
        setUpService();
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBadgeRepository.findAllByUser_Id(1L)).thenReturn(List.of());
        when(xpEventRepository.countGroupedByUser_Id(1L)).thenReturn(List.of());

        gamificationQueryService.getMyStatus(1L);

        org.mockito.Mockito.verify(xpEventRepository, org.mockito.Mockito.times(1)).countGroupedByUser_Id(1L);
        org.mockito.Mockito.verifyNoMoreInteractions(xpEventRepository);
    }

    @Test
    void HELPFUL_패밀리는_XpEvent_대신_리뷰_도메인의_조회_서비스를_쓴다() {
        setUpService();
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBadgeRepository.findAllByUser_Id(1L)).thenReturn(List.of());
        when(xpEventRepository.countGroupedByUser_Id(1L)).thenReturn(List.of());
        when(reviewQueryService.getTotalHelpfulReceived(1L)).thenReturn(30L);

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
