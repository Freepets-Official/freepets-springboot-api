package com.freepets.domain.gamification.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.gamification.converter.GamificationConverter;
import com.freepets.domain.gamification.dto.GamificationResponseDTO;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

/**
 * GET /api/v1/gamification/ranking — 전국 랭킹. 지금은 전국 스코프만 있다 — 시/도·시/군/구
 * 스코프는 "어느 지역 활동인지"를 XpEvent에 아직 기록하지 않아서(향후 활동 지역 기반으로
 * 갈 때 XP 적립 시점에 시설의 지역 코드를 같이 남겨야 한다) 이번 범위 밖이다.
 *
 * <p>매번 실시간으로 계산한다(별도 스냅샷 배치 없음) — 지금 사용자 규모에서는 매 요청마다
 * 전체를 다시 세는 비용이 무시할 만하고, 나중에 부담이 커지면 그때 스냅샷으로 옮기면 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingQueryService {

    // 참여자가 1명이면 "1등"이 의미가 없고, 2명이면 상대가 누군지 바로 특정된다.
    private static final long MIN_PARTICIPANTS_FOR_RANKING = 3;

    // 랭킹에 끼려면 XP가 이만큼은 있어야 한다 — 활동이 없으면 순위도 없다(#152). 목록·참여자
    // 수·내 순위 세 군데가 전부 이 값 하나를 보고 움직여야 서로 어긋나지 않는다.
    private static final long MINIMUM_XP_TO_PARTICIPATE = 1;

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final UserRepository userRepository;
    private final PetRepository petRepository;

    public GamificationResponseDTO.RankingResult getNationalRanking(
            Long viewerId,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        long offset = (long) safePage * safeSize;

        // 목록에서 빠지는 계정은 참여자 수에서도 빠져야 "N명 중 K번째"가 목록과 맞는다.
        long participantCount = userRepository
                .countByDeletedAtIsNullAndTotalXpGreaterThanEqual(MINIMUM_XP_TO_PARTICIPATE);

        List<UserRepository.RankingRow> rows =
                userRepository.findNationalRanking(MINIMUM_XP_TO_PARTICIPATE, safeSize, offset);
        Map<Long, Pet> representativePetByUserId = findRepresentativePets(rows);

        List<GamificationResponseDTO.RankingItem> items = rows.stream()
                .map(row -> {
                    Pet representativePet = representativePetByUserId.get(row.getId());
                    return GamificationConverter.toRankingItem(
                            row,
                            viewerId,
                            representativePet != null ? representativePet.getName() : null,
                            representativePet != null ? representativePet.getProfile() : null
                    );
                })
                .toList();

        GamificationResponseDTO.MyRanking me = viewerId != null
                ? buildMyRanking(viewerId, participantCount)
                : null;

        return new GamificationResponseDTO.RankingResult(
                "NATION",
                me,
                items,
                participantCount,
                LocalDateTime.now()
        );
    }

    // 랭킹 줄에 보여줄 "대표 반려동물"(가장 먼저 등록한 1마리) — userId별로 petId가 가장 작은
    // 것만 남긴다. petId 오름차순으로 정렬해서 받으므로 먼저 만난 값을 유지하면 된다.
    private Map<Long, Pet> findRepresentativePets(List<UserRepository.RankingRow> rows) {
        List<Long> userIds = rows.stream().map(UserRepository.RankingRow::getId).toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Pet> representativePetByUserId = new HashMap<>();
        for (Pet pet : petRepository.findAllByUserIdInAndDeletedAtIsNullOrderByUserIdAscPetIdAsc(userIds)) {
            representativePetByUserId.putIfAbsent(pet.getUser().getId(), pet);
        }
        return representativePetByUserId;
    }

    private GamificationResponseDTO.MyRanking buildMyRanking(
            Long viewerId,
            long participantCount
    ) {
        User viewer = userRepository.findByIdAndDeletedAtIsNull(viewerId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));

        // 목록에 없는 사람에게 순위를 주지 않는다 — XP가 0이면 랭킹 대상이 아니라서, 순위를
        // 매기면 "목록엔 없는데 내 순위는 34등"처럼 화면끼리 어긋난다(#152).
        boolean participates = viewer.getTotalXp() >= MINIMUM_XP_TO_PARTICIPATE;
        boolean ranked = participates && participantCount >= MIN_PARTICIPANTS_FOR_RANKING;
        Long rank = ranked
                ? 1 + userRepository.countByDeletedAtIsNullAndTotalXpGreaterThan(viewer.getTotalXp())
                : null;

        return GamificationConverter.toMyRanking(viewer, rank, participantCount, ranked);
    }

}
