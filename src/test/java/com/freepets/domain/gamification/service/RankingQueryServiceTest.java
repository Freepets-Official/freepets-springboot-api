package com.freepets.domain.gamification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.gamification.dto.GamificationResponseDTO;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class RankingQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    private RankingQueryService rankingQueryService;

    private void setUpService() {
        rankingQueryService = new RankingQueryService(userRepository);
    }

    private User user(
            Long id,
            String nickname,
            long totalXp,
            int level
    ) {
        User user = User.builder()
                .email(nickname + "@test.com")
                .passwordHash("hash")
                .nickname(nickname)
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        user.gainXp(totalXp, level);
        return user;
    }

    private UserRepository.RankingRow row(
            long rnk,
            Long id,
            String nickname,
            long totalXp,
            int level
    ) {
        return new UserRepository.RankingRow() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getNickname() {
                return nickname;
            }

            @Override
            public long getTotalXp() {
                return totalXp;
            }

            @Override
            public int getLevel() {
                return level;
            }

            @Override
            public long getRnk() {
                return rnk;
            }
        };
    }

    @Test
    void 게스트_조회는_me가_null이다() {
        setUpService();
        when(userRepository.countByDeletedAtIsNull()).thenReturn(340L);
        when(userRepository.findNationalRanking(anyInt(), anyLong()))
                .thenReturn(List.of(row(1, 8L, "1등", 9800L, 15)));

        GamificationResponseDTO.RankingResult result = rankingQueryService.getNationalRanking(null, 0, 20);

        assertThat(result.me()).isNull();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).isMe()).isFalse();
        verify(userRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void 로그인한_조회자는_본인_순위와_isMe가_채워진다() {
        setUpService();
        User me = user(12L, "나", 1240L, 6);
        when(userRepository.countByDeletedAtIsNull()).thenReturn(340L);
        when(userRepository.findNationalRanking(anyInt(), anyLong()))
                .thenReturn(List.of(row(1, 8L, "1등", 9800L, 15), row(2, 12L, "나", 1240L, 6)));
        when(userRepository.findByIdAndDeletedAtIsNull(12L)).thenReturn(Optional.of(me));
        // 참여자 340명 중 나보다 totalXp가 많은 사람이 11명이라 12번째.
        when(userRepository.countByDeletedAtIsNullAndTotalXpGreaterThan(1240L)).thenReturn(11L);

        GamificationResponseDTO.RankingResult result = rankingQueryService.getNationalRanking(12L, 0, 20);

        assertThat(result.me().rank()).isEqualTo(12L);
        assertThat(result.me().participantCount()).isEqualTo(340L);
        assertThat(result.me().ranked()).isTrue();
        assertThat(result.items().get(1).isMe()).isTrue();
        assertThat(result.items().get(0).isMe()).isFalse();
    }

    @Test
    void 참여자가_3명_미만이면_순위를_감춘다() {
        setUpService();
        User me = user(1L, "나", 50L, 2);
        when(userRepository.countByDeletedAtIsNull()).thenReturn(2L);
        when(userRepository.findNationalRanking(anyInt(), anyLong())).thenReturn(List.of());
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(me));

        GamificationResponseDTO.RankingResult result = rankingQueryService.getNationalRanking(1L, 0, 20);

        assertThat(result.me().ranked()).isFalse();
        assertThat(result.me().rank()).isNull();
        // 참여자가 너무 적어 순위 자체가 무의미할 때는 등수를 세는 카운트 쿼리 자체를 부르지 않는다.
        verify(userRepository, never()).countByDeletedAtIsNullAndTotalXpGreaterThan(anyLong());
    }

    @Test
    void 존재하지_않는_조회자면_예외를_던진다() {
        setUpService();
        when(userRepository.countByDeletedAtIsNull()).thenReturn(10L);
        when(userRepository.findNationalRanking(anyInt(), anyLong())).thenReturn(List.of());
        when(userRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rankingQueryService.getNationalRanking(999L, 0, 20))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorStatus.MEMBER4005);
    }

    @Test
    void size가_상한을_넘으면_50으로_잘린다() {
        setUpService();
        when(userRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(userRepository.findNationalRanking(eq(50), eq(0L))).thenReturn(List.of());

        rankingQueryService.getNationalRanking(null, 0, 999);

        verify(userRepository).findNationalRanking(50, 0L);
    }

    @Test
    void size가_0이하면_기본값_20을_쓴다() {
        setUpService();
        when(userRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(userRepository.findNationalRanking(eq(20), eq(0L))).thenReturn(List.of());

        rankingQueryService.getNationalRanking(null, 0, 0);

        verify(userRepository).findNationalRanking(20, 0L);
    }
}
