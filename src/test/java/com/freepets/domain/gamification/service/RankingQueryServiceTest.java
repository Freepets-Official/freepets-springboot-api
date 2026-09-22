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
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class RankingQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PetRepository petRepository;

    private RankingQueryService rankingQueryService;

    private void setUpService() {
        rankingQueryService = new RankingQueryService(userRepository, petRepository);
    }

    private Pet pet(
            Long petId,
            Long userId,
            String name,
            String photoUrl
    ) {
        Pet pet = Pet.builder().build();
        ReflectionTestUtils.setField(pet, "petId", petId);
        ReflectionTestUtils.setField(pet, "name", name);
        ReflectionTestUtils.setField(pet, "profile", photoUrl);
        User owner = User.builder()
                .email(userId + "@test.com")
                .passwordHash("hash")
                .nickname("owner" + userId)
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(owner, "id", userId);
        ReflectionTestUtils.setField(pet, "user", owner);
        return pet;
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
        when(userRepository.countByDeletedAtIsNullAndTotalXpGreaterThanEqual(1L)).thenReturn(340L);
        when(userRepository.findNationalRanking(anyLong(), anyInt(), anyLong()))
                .thenReturn(List.of(row(1, 8L, "1등", 9800L, 15)));

        GamificationResponseDTO.RankingResult result = rankingQueryService.getNationalRanking(null, 0, 20);

        assertThat(result.me()).isNull();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).isMe()).isFalse();
        // 대표 반려동물이 없으면 petName/petPhotoUrl은 null이다.
        assertThat(result.items().get(0).petName()).isNull();
        assertThat(result.items().get(0).petPhotoUrl()).isNull();
        verify(userRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void 가장_먼저_등록한_반려동물이_대표로_채워진다() {
        setUpService();
        when(userRepository.countByDeletedAtIsNullAndTotalXpGreaterThanEqual(1L)).thenReturn(1L);
        when(userRepository.findNationalRanking(anyLong(), anyInt(), anyLong()))
                .thenReturn(List.of(row(1, 8L, "1등", 9800L, 15)));
        // petId 오름차순으로 두 마리를 돌려주면, 서비스는 그중 첫 항목(가장 먼저 등록한 아이)만 써야 한다.
        when(petRepository.findAllByUserIdInAndDeletedAtIsNullOrderByUserIdAscPetIdAsc(List.of(8L)))
                .thenReturn(List.of(
                        pet(101L, 8L, "보리", "https://example.com/bori.jpg"),
                        pet(102L, 8L, "몽이", "https://example.com/mongi.jpg")
                ));

        GamificationResponseDTO.RankingResult result = rankingQueryService.getNationalRanking(null, 0, 20);

        assertThat(result.items().get(0).petName()).isEqualTo("보리");
        assertThat(result.items().get(0).petPhotoUrl()).isEqualTo("https://example.com/bori.jpg");
    }

    @Test
    void 로그인한_조회자는_본인_순위와_isMe가_채워진다() {
        setUpService();
        User me = user(12L, "나", 1240L, 6);
        when(userRepository.countByDeletedAtIsNullAndTotalXpGreaterThanEqual(1L)).thenReturn(340L);
        when(userRepository.findNationalRanking(anyLong(), anyInt(), anyLong()))
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
    void XP가_0인_조회자는_순위를_받지_않는다() {
        // #152 — 활동이 없으면 랭킹 목록에서도 빠지므로, 순위를 매기면 "목록엔 없는데 내 순위는
        // 34등"처럼 화면끼리 어긋난다.
        setUpService();
        User me = user(12L, "갓_가입한_사람", 0L, 1);
        when(userRepository.countByDeletedAtIsNullAndTotalXpGreaterThanEqual(1L)).thenReturn(340L);
        when(userRepository.findNationalRanking(anyLong(), anyInt(), anyLong()))
                .thenReturn(List.of(row(1, 8L, "1등", 9800L, 15)));
        when(userRepository.findByIdAndDeletedAtIsNull(12L)).thenReturn(Optional.of(me));

        GamificationResponseDTO.RankingResult result = rankingQueryService.getNationalRanking(12L, 0, 20);

        assertThat(result.me().ranked()).isFalse();
        assertThat(result.me().rank()).isNull();
        // 참여자 수와 내 XP는 그대로 내려간다 — 순위만 없을 뿐 화면에 보여줄 값은 필요하다.
        assertThat(result.me().participantCount()).isEqualTo(340L);
        assertThat(result.me().xp()).isZero();
        verify(userRepository, never()).countByDeletedAtIsNullAndTotalXpGreaterThan(anyLong());
    }

    @Test
    void 참여자가_적어도_활동이_있으면_순위를_준다() {
        // 참여자 수로 순위를 감추지 않는다(#152) — 목록(items)은 그대로 공개되므로 내 순위만
        // 가려봐야 화면끼리 어긋나기만 한다.
        setUpService();
        User me = user(1L, "나", 50L, 2);
        when(userRepository.countByDeletedAtIsNullAndTotalXpGreaterThanEqual(1L)).thenReturn(2L);
        when(userRepository.findNationalRanking(anyLong(), anyInt(), anyLong()))
                .thenReturn(List.of(row(1, 1L, "나", 50L, 2)));
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(me));
        when(userRepository.countByDeletedAtIsNullAndTotalXpGreaterThan(50L)).thenReturn(1L);

        GamificationResponseDTO.RankingResult result = rankingQueryService.getNationalRanking(1L, 0, 20);

        assertThat(result.me().ranked()).isTrue();
        assertThat(result.me().rank()).isEqualTo(2L);
        assertThat(result.me().participantCount()).isEqualTo(2L);
    }

    @Test
    void 존재하지_않는_조회자면_예외를_던진다() {
        setUpService();
        when(userRepository.countByDeletedAtIsNullAndTotalXpGreaterThanEqual(1L)).thenReturn(10L);
        when(userRepository.findNationalRanking(anyLong(), anyInt(), anyLong())).thenReturn(List.of());
        when(userRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rankingQueryService.getNationalRanking(999L, 0, 20))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorStatus.MEMBER4005);
    }

    @Test
    void size가_상한을_넘으면_50으로_잘린다() {
        setUpService();
        when(userRepository.countByDeletedAtIsNullAndTotalXpGreaterThanEqual(1L)).thenReturn(0L);
        when(userRepository.findNationalRanking(anyLong(), eq(50), eq(0L))).thenReturn(List.of());

        rankingQueryService.getNationalRanking(null, 0, 999);

        verify(userRepository).findNationalRanking(1L, 50, 0L);
    }

    @Test
    void size가_0이하면_기본값_20을_쓴다() {
        setUpService();
        when(userRepository.countByDeletedAtIsNullAndTotalXpGreaterThanEqual(1L)).thenReturn(0L);
        when(userRepository.findNationalRanking(anyLong(), eq(20), eq(0L))).thenReturn(List.of());

        rankingQueryService.getNationalRanking(null, 0, 0);

        verify(userRepository).findNationalRanking(1L, 20, 0L);
    }
}
