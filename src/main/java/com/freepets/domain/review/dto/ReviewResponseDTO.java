package com.freepets.domain.review.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.review.entity.Tag;

public class ReviewResponseDTO {

    private ReviewResponseDTO() {}

    public record Grade(
            int level,
            String label,
            double score,
            long count,
            long needMore
    ) {}

    public record CategoryAverages(
            double space,
            double staff,
            double amenity
    ) {}

    public record TagCount(
            Tag tag,
            long count
    ) {}

    public record PetInfo(
            Long petId,
            Kind kind,
            String species,
            BigDecimal weight
    ) {}

    public record ReviewDetail(
            Long reviewId,
            Long facilityId,
            Long userId,
            String nickname,

            // record 접근자가 isShowPetInfo()이므로 JSON 프로퍼티명을 showPetInfo로 명시 고정
            @JsonProperty("showPetInfo")
            boolean isShowPetInfo,
            List<PetInfo> pets,
            Integer ratingSpace,
            Integer ratingStaff,
            Integer ratingAmenity,
            int score100,
            String content,
            List<Tag> tags,
            LocalDate visitedAt,
            boolean reportedByMe
    ) {}

    public record PageInfo(
            int page,
            int size,
            long totalElements,
            boolean hasNext
    ) {}

    public record ReviewListResult(
            Grade grade,
            CategoryAverages categoryAverages,
            List<TagCount> topTags,
            List<ReviewDetail> reviews,
            PageInfo pageInfo
    ) {}

    public record UpsertResult(
            Long reviewId,
            Long facilityId,
            List<Long> petIds,

            @JsonProperty("showPetInfo")
            boolean isShowPetInfo,
            Integer ratingSpace,
            Integer ratingStaff,
            Integer ratingAmenity,
            String content,
            List<Tag> tags,
            LocalDate visitedAt
    ) {}

    public record DeleteResult(
            Long reviewId
    ) {}

    public record ReportResult(
            Long reviewId
    ) {}
}
