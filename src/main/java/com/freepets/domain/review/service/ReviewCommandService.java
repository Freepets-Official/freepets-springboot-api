package com.freepets.domain.review.service;

import java.time.LocalDate;
import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.service.FacilityGradeCacheService;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReviewCommandService {

    // Supabase에 수동으로 만든 부분 유니크 인덱스 이름과 맞춰둔다(엔티티에 @UniqueConstraint로
    // 표현할 수 없는 partial index라 DDL로 직접 생성했다).
    private static final String FACILITY_USER_UNIQUE_CONSTRAINT = "uq_reviews_facility_user_active";

    private final ReviewRepository reviewRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final PetCheckRepository petCheckRepository;

    // 리뷰가 바뀌면 시설의 친화도 점수·리뷰 수·발자국 등급을 다시 계산해둔다. 발자국 랭킹이
    // 전체 시설을 점수순으로 정렬해야 해서, 조회 시점에 집계하면 매 요청마다 리뷰 전체를 훑게 된다.
    //
    // 신고(reportReview)는 PENDING으로 저장되고 집계는 ACCEPTED만 제외하므로 여기서 갱신하지
    // 않는다. 신고를 승인하는 기능이 생기면 그 지점에 추가해야 한다.
    private final FacilityGradeCacheService facilityGradeCacheService;

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

        List<Pet> pets = findOwnedPets(userId, request.getPetIds());
        List<Tag> tags = distinctTags(request.getTags());

        Review existingReview = reviewRepository
                .findByFacilityFacilityIdAndUserIdAndDeletedAtIsNull(facilityId, userId)
                .orElse(null);
        Review review = existingReview == null
                ? createReview(request, facility, user)
                : updateReview(existingReview, request);

        review.replacePets(pets);
        review.replaceTags(tags);

        Review savedReview = saveReview(review);

        facilityGradeCacheService.refresh(facilityId);

        return ReviewConverter.toUpsertResult(savedReview);
    }

    private Review createReview(
            ReviewRequestDTO.UpsertRequest request,
            Facility facility,
            User user
    ) {
        // 방문일은 실제로 다녀온 날짜라 최초 작성 시에만 정하고, 그 뒤로는 수정해도 바뀌지 않는다.
        LocalDate visitedAt = request.getVisitedAt() != null ? request.getVisitedAt() : LocalDate.now();
        return ReviewConverter.toReview(request, facility, user, visitedAt);
    }

    private Review updateReview(
            Review review,
            ReviewRequestDTO.UpsertRequest request
    ) {
        review.update(
                request.getRatingSpace(),
                request.getRatingStaff(),
                request.getRatingAmenity(),
                request.getContent(),
                request.isShowPetInfo()
        );
        return review;
    }

    // 신규 insert일 때는 Review가 GenerationType.IDENTITY라 save() 호출 시점에 바로 INSERT가
    // 나가서 여기서 제약 위반을 잡을 수 있다. 나중에 시퀀스 전략으로 바뀌면 flush가 커밋
    // 시점(이 메소드 밖)으로 밀려서 이 catch가 더는 못 잡게 되니 주의.
    private Review saveReview(Review review) {
        try {
            return reviewRepository.save(review);
        } catch (DataIntegrityViolationException exception) {
            // 동시에 두 번 제출되면 둘 다 "기존 리뷰 없음"으로 보고 insert를 시도할 수 있다.
            // DB의 부분 유니크 인덱스(시설+유저, 삭제되지 않은 리뷰)가 뒤늦은 쪽을 막아준다.
            //
            // 다만 DataIntegrityViolationException은 FK 위반·not-null 위반 등 다른 무결성
            // 오류도 함께 잡히므로, 실제로 이 유니크 인덱스가 원인일 때만 409로 바꾸고
            // 그 외에는 원인을 숨기지 않고 그대로 올린다.
            if (!isUniqueConstraintViolation(exception, FACILITY_USER_UNIQUE_CONSTRAINT)) {
                throw exception;
            }

            log.warn(
                    "리뷰 저장 중 유니크 인덱스({}) 충돌: reviewId={}",
                    FACILITY_USER_UNIQUE_CONSTRAINT, review.getReviewId(), exception
            );
            throw new GeneralException(ErrorStatus.REVIEW4004);
        }
    }

    // getMostSpecificCause()는 원인 체인의 가장 아래(SQLException)까지 내려가버려서
    // 중간에 있는 Hibernate의 ConstraintViolationException을 지나쳐버린다. 제약 이름은
    // 그 예외가 들고 있으므로, 체인을 직접 순회하며 처음 만나는 걸 찾는다.
    private boolean isUniqueConstraintViolation(
            DataIntegrityViolationException exception,
            String constraintName
    ) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return constraintName.equals(constraintViolation.getConstraintName());
            }
        }
        return false;
    }

    // petIds에 중복이 섞여 오면 소유자 검증 쿼리가 그만큼 반복 실행되고
    // review_pets에도 중복 행이 쌓이므로 미리 걸러낸다.
    private List<Pet> findOwnedPets(
            Long userId,
            List<Long> petIds
    ) {
        return petIds.stream()
                .distinct()
                .map(petId -> findOwnedPet(userId, petId))
                .toList();
    }

    private List<Tag> distinctTags(List<Tag> tags) {
        return tags == null ? List.of() : tags.stream().distinct().toList();
    }

    public ReviewResponseDTO.DeleteResult deleteReview(
            Long userId,
            Long reviewId
    ) {
        Review review = findOwnedReview(userId, reviewId);
        review.delete();

        facilityGradeCacheService.refresh(review.getFacility().getFacilityId());

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
