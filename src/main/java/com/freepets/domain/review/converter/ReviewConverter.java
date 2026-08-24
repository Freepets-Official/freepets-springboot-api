package com.freepets.domain.review.converter;

import java.time.LocalDate;
import java.util.List;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.review.dto.ReviewRequestDTO;
import com.freepets.domain.review.dto.ReviewResponseDTO;
import com.freepets.domain.review.entity.Review;
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

    public static ReviewResponseDTO.PetInfo toPetInfo(Pet pet) {
        return new ReviewResponseDTO.PetInfo(
                pet.getPetId(),
                pet.getKind(),
                pet.getSpecies(),
                pet.getWeight()
        );
    }

    public static ReviewResponseDTO.ReviewDetail toReviewDetail(
            Review review,
            boolean reportedByMe
    ) {
        // showPetInfo=false면 서버 응답 자체에서 반려동물 정보를 빼서, 클라이언트가 플래그만 보고
        // 숨기는 방식(이미 노출된 데이터를 화면에서만 가리는 것)이 되지 않게 한다.
        List<ReviewResponseDTO.PetInfo> pets = review.isShowPetInfo()
                ? review.getReviewPets().stream().map(reviewPet -> toPetInfo(reviewPet.getPet())).toList()
                : List.of();
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
                review.toScore100(),
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
