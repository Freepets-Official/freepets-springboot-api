package com.freepets.domain.facility.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;

public class FacilityResponseDTO {

    private FacilityResponseDTO() {}

    /**
     * 시설 목록 한 건.
     *
     * <p>{@code distanceM}·{@code reviewCnt}는 다른 네이밍 규칙과 달리 줄여 썼다.
     * 프론트가 코딩하는 계약이라 API 명세서 표기를 그대로 따랐다.
     */
    public record FacilitySummary(
            Long facilityId,
            String name,
            FacilityCategory category,
            String address,
            long distanceM,
            PetAllowed petAllowed,
            BigDecimal maxWeight,
            List<Requirement> requirements,

            // 친화도 집계가 아직 없어 당분간 항상 null이다. 리뷰 API가 붙으면 이 API 수정 없이 채워진다.
            Integer petScore,
            String rating,
            long reviewCnt
    ) {}

    public record FacilitySearchResult(
            List<FacilitySummary> items,
            long total
    ) {}

    /**
     * 발자국 등급. 어느 등급에도 못 미치면 {@code level}이 0이고
     * {@code label}은 {@code "리뷰 수집 중 (n/10)"}이 된다.
     */
    public record PawGrade(
            int level,
            String label
    ) {}

    /**
     * 리뷰에서 즉시 계산한 평점. 적격 리뷰가 한 건도 없으면 이 객체 자체가 {@code null}이다.
     *
     * <p>0.0을 내려주면 화면이 "최악의 시설"로 그리기 때문에 0이 아니라 없음으로 구분한다.
     *
     * <p>{@code facilities}의 캐시 컬럼(pet_score 등)은 쓰지 않는다. 값을 채우는 코드가 아직
     * 없어 전부 null이기 때문이다.
     */
    public record Ratings(
            double score,
            double spaceRating,
            double customerService,
            double amenitiesRating
    ) {}

    /** 조회한 사용자의 반려동물. 이 시설에 데려갈 수 있는지 화면이 판단할 재료다. */
    public record OwnedPet(
            Long petId,
            String name,
            BigDecimal weight
    ) {}

    /**
     * 시설 상세.
     *
     * <p>리뷰 목록과 만족도 목록은 담지 않는다. 각각 전용 API가 있고 페이징 단위와 갱신 주기가
     * 달라 한 응답에 묶으면 리뷰 한 건을 더 보려고 시설 정보까지 다시 받게 된다.
     *
     * <p>필드 선언 순서가 곧 JSON 순서다. API 명세서 순서를 따랐다.
     */
    public record FacilityDetail(
            Long facilityId,
            String name,
            FacilityCategory category,
            String address,
            String phone,
            Double latitude,
            Double longitude,

            // 요청에 좌표가 없거나 시설에 좌표가 없으면 null이다.
            Long distanceM,
            PetAllowed petAllowed,

            // 화면에 그대로 보여줄 동반 조건 안내문. 아직 채우지 않은 시설은 null이다.
            String petConditionRaw,
            LocalDateTime confirmedAt,
            String imageUrl,
            String thumbnailUrl,
            PawGrade pawGrade,
            Ratings ratings,
            List<OwnedPet> pets,
            boolean hasNonDogCatPet
    ) {}
}
