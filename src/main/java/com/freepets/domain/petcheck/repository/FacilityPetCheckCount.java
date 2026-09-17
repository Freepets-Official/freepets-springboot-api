package com.freepets.domain.petcheck.repository;

/** 시설별 판별 수 집계 결과. 판별이 한 건도 없는 시설은 결과에 나타나지 않으니 호출부에서 0으로 채운다. */
public record FacilityPetCheckCount(
        Long facilityId,
        long checkCount
) {}
