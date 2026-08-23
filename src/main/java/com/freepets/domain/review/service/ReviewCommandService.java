package com.freepets.domain.review.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.review.converter.ReviewConverter;
import com.freepets.domain.review.dto.ReviewRequestDTO;
import com.freepets.domain.review.dto.ReviewResponseDTO;
import com.freepets.domain.review.entity.Review;
import com.freepets.domain.review.entity.ReviewReport;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.entity.Tag;
import com.freepets.domain.review.repository.ReviewReportRepository;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewCommandService {

    private final ReviewRepository reviewRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final PetCheckRepository petCheckRepository;

    public ReviewResponseDTO.UpsertResult upsertReview(
            Long userId,
            Long facilityId,
            ReviewRequestDTO.UpsertRequest request
    ) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4041));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));

        validateEligiblePets(request.getPetIds(), facilityId);

        Review review = reviewRepository.findByFacilityFacilityIdAndUserId(facilityId, userId)
                .orElse(null);

        if (review == null) {
            review = ReviewConverter.toReview(request, facility, user, LocalDate.now());
        } else {
            review.update(
                    request.getRatingSpace(),
                    request.getRatingStaff(),
                    request.getRatingAmenity(),
                    request.getContent(),
                    request.isShowPetInfo(),
                    LocalDate.now()
            );
            review.getReviewPets().clear();
            review.getTags().clear();
        }

        for (Long petId : request.getPetIds()) {
            Pet pet = petRepository.findByPetIdAndDeletedAtIsNull(petId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.PET4001));
            review.getReviewPets().add(ReviewConverter.toReviewPet(review, pet));
        }

        List<Tag> tags = request.getTags() == null ? List.of() : request.getTags();
        for (Tag tag : tags) {
            review.getTags().add(ReviewConverter.toReviewTag(review, tag));
        }

        Review savedReview = reviewRepository.save(review);

        return ReviewConverter.toUpsertResult(savedReview);
    }

    public ReviewResponseDTO.DeleteResult deleteReview(
            Long userId,
            Long reviewId
    ) {
        Review review = findOwnedReview(userId, reviewId);
        reviewRepository.delete(review);

        return ReviewConverter.toDeleteResult(review);
    }

    public ReviewResponseDTO.ReportResult reportReview(
            Long userId,
            Long reviewId,
            ReviewRequestDTO.ReportRequest request
    ) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.REVIEW4041));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));

        if (reviewReportRepository.existsByReviewReviewIdAndUserId(reviewId, userId)) {
            throw new GeneralException(ErrorStatus.REVIEW4003);
        }

        ReviewReport reviewReport = ReviewReport.builder()
                .review(review)
                .user(user)
                .reason(request.getReason())
                .status(ReviewReportStatus.PENDING)
                .build();
        ReviewReport savedReport = reviewReportRepository.save(reviewReport);

        return ReviewConverter.toReportResult(savedReport);
    }

    private void validateEligiblePets(
            List<Long> petIds,
            Long facilityId
    ) {
        List<Long> ineligiblePetIds = petIds.stream()
                .filter(petId -> !petCheckRepository.existsByPetPetIdAndFacilityFacilityId(petId, facilityId))
                .toList();

        if (!ineligiblePetIds.isEmpty()) {
            throw new GeneralException(ErrorStatus.REVIEW4001, Map.of("ineligiblePetIds", ineligiblePetIds));
        }
    }

    private Review findOwnedReview(
            Long userId,
            Long reviewId
    ) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.REVIEW4041));

        if (!review.isOwnedBy(userId)) {
            throw new GeneralException(ErrorStatus.REVIEW4002);
        }

        return review;
    }
}
