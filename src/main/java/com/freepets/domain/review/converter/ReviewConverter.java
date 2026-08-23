package com.freepets.domain.review.converter;

import java.time.LocalDate;
import java.util.List;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.review.dto.ReviewRequestDTO;
import com.freepets.domain.review.dto.ReviewResponseDTO;
import com.freepets.domain.review.entity.Review;
import com.freepets.domain.review.entity.ReviewPet;
import com.freepets.domain.review.entity.ReviewReport;
import com.freepets.domain.review.entity.ReviewTag;
import com.freepets.domain.review.entity.Tag;
import com.freepets.domain.user.entity.User;

public class ReviewConverter {

    private ReviewConverter() {}

    public static Review toReview(
            ReviewRequestDTO.UpsertRequest request,
            Facility facility,
            User user,
            LocalDate visitedAt
    ) {
        return Review.builder()
                .facility(facility)
                .user(user)
                .ratingSpace(request.getRatingSpace())
                .ratingStaff(request.getRatingStaff())
                .ratingAmenity(request.getRatingAmenity())
                .content(request.getContent())
                .isShowPetInfo(request.isShowPetInfo())
                .visitedAt(visitedAt)
                .build();
    }

    public static ReviewPet toReviewPet(
            Review review,
            Pet pet
    ) {
        return ReviewPet.builder()
                .review(review)
                .pet(pet)
                .build();
    }

    public static ReviewTag toReviewTag(
            Review review,
            Tag tag
    ) {
        return ReviewTag.builder()
                .review(review)
                .tag(tag)
                .build();
    }

    public static ReviewResponseDTO.PetInfo toPetInfo(Pet pet) {
        return new ReviewResponseDTO.PetInfo(
                pet.getPetId(),
                pet.getKind(),
                pet.getSpecies(),
                pet.getWeight()
        );
    }

    // 친화도 점수(0~100) = (공간×0.35 + 직원친절도×0.35 + 편의시설×0.30) / 5 * 100 (산출물4 공식)
    public static int toScore100(Review review) {
        double weighted = review.getRatingSpace() * 0.35
                + review.getRatingStaff() * 0.35
                + review.getRatingAmenity() * 0.30;
        return (int) Math.round(weighted / 5.0 * 100);
    }

    public static ReviewResponseDTO.ReviewDetail toReviewDetail(
            Review review,
            boolean reportedByMe
    ) {
        List<ReviewResponseDTO.PetInfo> pets = review.getReviewPets().stream()
                .map(reviewPet -> toPetInfo(reviewPet.getPet()))
                .toList();
        List<Tag> tags = review.getTags().stream()
                .map(ReviewTag::getTag)
                .toList();

        return new ReviewResponseDTO.ReviewDetail(
                review.getReviewId(),
                review.getFacility().getFacilityId(),
                review.getUser().getId(),
                review.getUser().getNickname(),
                review.isShowPetInfo(),
                pets,
                review.getRatingSpace(),
                review.getRatingStaff(),
                review.getRatingAmenity(),
                toScore100(review),
                review.getContent(),
                tags,
                review.getVisitedAt(),
                reportedByMe
        );
    }

    public static ReviewResponseDTO.UpsertResult toUpsertResult(Review review) {
        List<Long> petIds = review.getReviewPets().stream()
                .map(reviewPet -> reviewPet.getPet().getPetId())
                .toList();
        List<Tag> tags = review.getTags().stream()
                .map(ReviewTag::getTag)
                .toList();

        return new ReviewResponseDTO.UpsertResult(
                review.getReviewId(),
                review.getFacility().getFacilityId(),
                petIds,
                review.isShowPetInfo(),
                review.getRatingSpace(),
                review.getRatingStaff(),
                review.getRatingAmenity(),
                review.getContent(),
                tags,
                review.getVisitedAt()
        );
    }

    public static ReviewResponseDTO.DeleteResult toDeleteResult(Review review) {
        return new ReviewResponseDTO.DeleteResult(review.getReviewId());
    }

    public static ReviewResponseDTO.ReportResult toReportResult(ReviewReport reviewReport) {
        return new ReviewResponseDTO.ReportResult(reviewReport.getReview().getReviewId());
    }
}
