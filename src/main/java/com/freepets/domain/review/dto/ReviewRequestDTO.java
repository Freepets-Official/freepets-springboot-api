package com.freepets.domain.review.dto;

import java.time.LocalDate;
import java.util.List;

import com.freepets.domain.review.entity.ReviewReportReason;
import com.freepets.domain.review.entity.Tag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class ReviewRequestDTO {

    private ReviewRequestDTO() {}

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpsertRequest {

        @NotEmpty(message = "반려동물은 최소 1마리 이상 선택해야 합니다.")
        private List<Long> petIds;

        private boolean isShowPetInfo;

        @NotNull(message = "공간 점수는 필수입니다.")
        @Min(value = 1, message = "공간 점수는 1점 이상이어야 합니다.")
        @Max(value = 5, message = "공간 점수는 5점 이하여야 합니다.")
        private Integer ratingSpace;

        @NotNull(message = "직원 친절도 점수는 필수입니다.")
        @Min(value = 1, message = "직원 친절도 점수는 1점 이상이어야 합니다.")
        @Max(value = 5, message = "직원 친절도 점수는 5점 이하여야 합니다.")
        private Integer ratingStaff;

        @NotNull(message = "편의시설 점수는 필수입니다.")
        @Min(value = 1, message = "편의시설 점수는 1점 이상이어야 합니다.")
        @Max(value = 5, message = "편의시설 점수는 5점 이하여야 합니다.")
        private Integer ratingAmenity;

        @NotBlank(message = "리뷰 내용은 필수입니다.")
        private String content;

        private List<Tag> tags;

        // 생략하면 신규 작성 시 오늘 날짜, 수정 시 기존 방문일을 그대로 유지한다.
        @PastOrPresent(message = "방문일은 오늘 이전 날짜여야 합니다.")
        private LocalDate visitedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ReportRequest {

        @NotNull(message = "신고 사유는 필수입니다.")
        private ReviewReportReason reason;
    }
}
