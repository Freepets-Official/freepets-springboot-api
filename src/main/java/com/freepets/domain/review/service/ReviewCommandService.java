package com.freepets.domain.review.service;

import java.time.LocalDate;
import java.util.List;

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

        validateFacilityEligibility(userId, facilityId);

        Review review = reviewRepository.findByFacilityFacilityIdAndUserIdAndDeletedAtIsNull(facilityId, userId)
                .orElse(null);

        // 방문일을 안 보내면 신규는 오늘, 수정은 기존 방문일을 그대로 둔다 — 매번 오늘로 덮어써서
        // 과거 방문일이 사라지지 않게 한다.
        LocalDate visitedAt = request.getVisitedAt() != null
                ? request.getVisitedAt()
                : (review == null ? LocalDate.now() : review.getVisitedAt());

        if (review == null) {
            review = ReviewConverter.toReview(request, facility, user, visitedAt);
        } else {
            review.update(
                    request.getRatingSpace(),
                    request.getRatingStaff(),
                    request.getRatingAmenity(),
                    request.getContent(),
                    request.isShowPetInfo(),
                    visitedAt
            );
            review.getReviewPets().clear();
            review.getTags().clear();
        }

        for (Long petId : request.getPetIds()) {
            Pet pet = findOwnedPet(userId, petId);
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
        review.delete();

        return ReviewConverter.toDeleteResult(review);
    }

    public ReviewResponseDTO.ReportResult reportReview(
            Long userId,
            Long reviewId,
            ReviewRequestDTO.ReportRequest request
    ) {
        Review review = reviewRepository.findByReviewIdAndDeletedAtIsNull(reviewId)
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

    // 반려동물 단위가 아니라 시설 단위로 확인한다 — 새·토끼처럼 개별 판별 자체가 없는 종도
    // petIds에 자유롭게 포함할 수 있어야 하므로, "이 유저가 이 시설에서 판별을 받은 적이 있는지"만 본다.
    private void validateFacilityEligibility(
            Long userId,
            Long facilityId
    ) {
        if (!petCheckRepository.existsByUserIdAndFacilityFacilityId(userId, facilityId)) {
            throw new GeneralException(ErrorStatus.REVIEW4001);
        }
    }

    // petId만 믿고 조회하면 남의 반려동물을 내 리뷰에 붙일 수 있어(IDOR) 소유자 검증까지 한다.
    private Pet findOwnedPet(
            Long userId,
            Long petId
    ) {
        Pet pet = petRepository.findByPetIdAndDeletedAtIsNull(petId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.PET4001));

        if (!pet.isOwnedBy(userId)) {
            throw new GeneralException(ErrorStatus.PET4002);
        }

        return pet;
    }

    private Review findOwnedReview(
            Long userId,
            Long reviewId
    ) {
        Review review = reviewRepository.findByReviewIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.REVIEW4041));

        if (!review.isOwnedBy(userId)) {
            throw new GeneralException(ErrorStatus.REVIEW4002);
        }

        return review;
    }
}
