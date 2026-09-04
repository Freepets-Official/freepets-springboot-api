package com.freepets.domain.review.repository;

import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;

/**
 * 시설 한 곳에 리뷰를 남긴 반려동물 한 마리의 (종, 크기) — {@link
 * ReviewPetRepository#findKindAndBreedSizeByFacilityIdIn}의 다건 결과. 후보 시설 여러 곳의
 * "이 시설을 다녀간 반려동물들" 정보를 한 번에 묶어 조회할 때, 시설별로 다시 그룹핑하는 데 쓴다.
 * breedSize는 기록이 없으면 null일 수 있다.
 */
public record FacilityPetProfile(
        Long facilityId,
        Kind kind,
        BreedSize breedSize
) {}
