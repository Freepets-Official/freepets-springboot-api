package com.freepets.domain.review.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.review.entity.ReviewReportReason;
import com.freepets.domain.review.entity.ReviewReportStatus;
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
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
            boolean reportedByMe,
            long helpfulCount,

            // record 접근자가 isHelpfulByMe()이므로 JSON 프로퍼티명을 helpfulByMe로 명시 고정
            @JsonProperty("helpfulByMe")
            boolean isHelpfulByMe,

            // 방문 인증샷(선택). 없으면 @JsonInclude(NON_NULL)로 응답 JSON에서 키 자체가 빠진다.
            String photoUrl
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
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
            LocalDate visitedAt,
            String photoUrl
    ) {}

    public record DeleteResult(
            Long reviewId
    ) {}

    public record ReportResult(
            Long reviewId
    ) {}

    public record HelpfulResult(
            Long reviewId,
            long helpfulCount
    ) {}

    /** 관리자 신고 목록. 신고를 리뷰 단위로 묶고, 첫 신고가 오래된 순이다. */
    public record AdminReportedReviewList(
            List<AdminReportedReview> reviews,
            PageInfo pageInfo
    ) {}

    /**
     * 관리자가 숨길지 판단하는 데 필요한 리뷰 원문과 신고 현황.
     *
     * @param reportCount     조회한 상태(예: 대기)의 신고 수
     * @param reasonCounts    사유별 신고 수. 신고가 없는 사유는 키가 없다
     * @param firstReportedAt 조회한 상태의 신고 중 가장 먼저 접수된 시각
     */
    public record AdminReportedReview(
            Long reviewId,
            Long facilityId,
            String facilityName,
            Long authorUserId,
            String authorNickname,
            String content,
            String photoUrl,
            Integer ratingSpace,
            Integer ratingStaff,
            Integer ratingAmenity,
            LocalDateTime reviewCreatedAt,
            long reportCount,
            Map<ReviewReportReason, Long> reasonCounts,
            LocalDateTime firstReportedAt
    ) {}

    /**
     * @param status               처리 후 신고 상태(승인 또는 반려)
     * @param processedReportCount 이번에 처리한 대기 신고 수
     */
    public record AdminReportActionResult(
            Long reviewId,
            ReviewReportStatus status,
            int processedReportCount
    ) {}
}
