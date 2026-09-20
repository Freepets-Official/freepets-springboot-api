package com.freepets.domain.pet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.petcheck.service.PetCheckQueryService;
import com.freepets.domain.petsatisfaction.service.PetSatisfactionQueryService;
import com.freepets.domain.pet.dto.PetResponseDTO;
import com.freepets.domain.review.service.ReviewQueryService;
import com.freepets.domain.stamp.service.StampQueryService;

import lombok.RequiredArgsConstructor;

/**
 * "함께한 발자국"(freepets-docs PR #49, docs/12 0절) 계산 — 반려동물은 레벨을 갖지 않고, 대신
 * 그 아이가 낀 판별·도장·만족도 횟수의 합으로 표시한다. 리뷰는 참고용으로만 같이 보여주고
 * 합계({@code total})에는 넣지 않는다 — 코스 공개·복사·거부 제보처럼 리뷰도 "특정 아이의 행동"
 * 으로 쪼개기 애매한 값은 아니지만, 문서가 확정한 발자국 정의 자체가 판별·도장·만족도 셋뿐이다.
 *
 * <p>네 도메인(판별·만족도·리뷰·도장)의 기존 QueryService를 조합하기만 한다 — 각 도메인의
 * repository를 여기서 직접 물지 않고, 그 도메인이 이미 소유한 count 메서드를 통해서만
 * 가져온다(계층 규칙).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PetPawPrintQueryService {

    private final PetCheckQueryService petCheckQueryService;
    private final PetSatisfactionQueryService petSatisfactionQueryService;
    private final ReviewQueryService reviewQueryService;
    private final StampQueryService stampQueryService;

    public PetResponseDTO.PawPrintStats getPawPrintStats(Long petId) {
        long checkCount = petCheckQueryService.countForPet(petId);
        long satisfactionCount = petSatisfactionQueryService.countForPet(petId);
        long stampCount = stampQueryService.countForPet(petId);
        long reviewCount = reviewQueryService.countForPet(petId);
        long total = checkCount + satisfactionCount + stampCount;

        return new PetResponseDTO.PawPrintStats(checkCount, reviewCount, satisfactionCount, stampCount, total);
    }

}
