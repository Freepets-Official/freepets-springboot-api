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
import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.gamification.service.GamificationService;
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
import com.freepets.domain.review.repository.ReviewHelpfulRepository;
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

    // review_helpfuls 유니크 제약(review_id, user_id) 이름.
    private static final String REVIEW_HELPFUL_UNIQUE_CONSTRAINT = "uk_review_helpfuls_review_user";

    private final ReviewRepository reviewRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final ReviewHelpfulRepository reviewHelpfulRepository;
    private final ReviewHelpfulRecorder reviewHelpfulRecorder;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final PetCheckRepository petCheckRepository;
    private final GamificationService gamificationService;

    // 리뷰 신규 작성 1건당 지급하는 경험치(수정은 미지급). 사진 첨부 가산점은 리뷰에 사진 필드
    // 자체가 아직 없어 보류했다.
    private static final int REVIEW_XP = 20;

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
        boolean isNewReview = existingReview == null;
        Review review = isNewReview
                ? createReview(request, facility, user)
                : existingReview;
        applyRequestToReview(review, request, pets, tags);

        Review savedReview = saveReview(review);

        facilityGradeCacheService.refresh(facilityId);

        if (isNewReview) {
            gamificationService.grantXp(userId, XpSourceType.REVIEW, savedReview.getReviewId(), REVIEW_XP);
        }

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

    // upsertReview(신규/수정 공통)와 updateReview(PUT) 둘 다 쓴다 — 신규 리뷰에도 그대로 태운다.
    // createReview가 이미 같은 request로 값을 채워서 update() 호출이 중복이지만, 분기를 나눠서
    // 따로 관리하는 것보다 한 곳에서만 고치면 두 경로가 갈라질 일이 없는 쪽을 택했다.
    private void applyRequestToReview(
            Review review,
            ReviewRequestDTO.UpsertRequest request,
            List<Pet> pets,
            List<Tag> tags
    ) {
        review.update(
                request.getRatingSpace(),
                request.getRatingStaff(),
                request.getRatingAmenity(),
                request.getContent(),
                request.isShowPetInfo()
        );
        review.replacePets(pets);
        review.replaceTags(tags);
    }

    /**
     * PUT /api/v1/reviews/{reviewId} — 별점·내용만 고치려 해도 지금까지는 삭제 후 재작성뿐이었다
     * (facilityId 기준 upsertReview는 시설 컨텍스트가 있어야 호출 가능해 이 화면과는 안 맞는다).
     * reviewId 하나로 바로 수정한다 — upsertReview와 같은 applyRequestToReview를 reviewId
     * 기준 조회·소유권 검증으로만 감싼 것이다. 방문일은 update()가 안 받아서(엔티티 참고)
     * 여기서도 그대로 유지된다. 새 리뷰가 아니라 경험치는 지급하지 않는다.
     */
    public ReviewResponseDTO.UpsertResult updateReview(
            Long userId,
            Long reviewId,
            ReviewRequestDTO.UpsertRequest request
    ) {
        Review review = findOwnedReview(userId, reviewId);
        List<Pet> pets = findOwnedPets(userId, request.getPetIds());
        List<Tag> tags = distinctTags(request.getTags());

        applyRequestToReview(review, request, pets, tags);

        facilityGradeCacheService.refresh(review.getFacility().getFacilityId());

        return ReviewConverter.toUpsertResult(review);
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

    /**
     * POST /api/v1/reviews/{reviewId}/helpful — "도움됐어요" 표시. 존재 여부가 곧 표시 상태라
     * CalendarMedLog와 같은 방식 — 이미 표시한 리뷰에 다시 눌러도 에러 없이 그대로 성공
     * 처리한다(멱등). 취소(un-mark)는 아직 없다 — 필요해지면 DELETE로 추가하면 된다.
     *
     * <p>본인 리뷰는 표시할 수 없다 — 작성자 본인이 자기 리뷰를 눌러 카운트를 스스로
     * 올리는 걸 막는다(CourseCommandService의 자기 복사 방지와 같은 이유).
     */
    public ReviewResponseDTO.HelpfulResult markHelpful(
            Long userId,
            Long reviewId
    ) {
        Review review = reviewRepository.findByReviewIdAndDeletedAtIsNull(reviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.REVIEW4041));

        if (review.isOwnedBy(userId)) {
            throw new GeneralException(ErrorStatus.REVIEW4005);
        }

        if (!reviewHelpfulRepository.existsByReviewReviewIdAndUserId(reviewId, userId)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
            recordHelpfulIfAbsent(review, user);
        }

        // incrementHelpfulCount는 영속성 컨텍스트를 거치지 않는 벌크 업데이트라(ReviewRepository
        // 참고) 위에서 로드해둔 review의 메모리 값이 안 바뀐다 — 최신 값을 응답하려면 다시 읽어야
        // 한다. 이 사이에 리뷰가 지워지는 등의 극단적인 경우엔 방금 읽은 값을 그대로 쓴다.
        Review refreshed = reviewRepository.findByReviewIdAndDeletedAtIsNull(reviewId).orElse(review);
        return ReviewConverter.toHelpfulResult(refreshed);
    }

    /**
     * uk_review_helpfuls_review_user가 마지막 방어선이다 — exists 확인과 저장 사이에 거의
     * 동시에 두 번 눌리면 유니크 제약이 뒤늦은 쪽을 막아준다. 이미 표시된 것으로 보고 조용히
     * 넘어간다(멱등 — 원래 액션이 실패해야 할 이유가 없다).
     *
     * <p>저장 자체는 {@link ReviewHelpfulRecorder}로 별도 트랜잭션에 격리해서 부른다 —
     * PostgreSQL은 트랜잭션 안에서 문장 하나가 실패하면 트랜잭션 전체가 "aborted" 상태가
     * 돼서, 이 메소드가 쓰는 트랜잭션 안에서 그대로 저장을 시도했다면 여기서 예외를 잡아도
     * 커밋 시점에 JPA가 RollbackException을 뒤늦게 던진다(그 클래스 주석 참고).
     */
    private void recordHelpfulIfAbsent(
            Review review,
            User marker
    ) {
        User author = review.getUser();

        try {
            reviewHelpfulRecorder.record(review, marker);
        } catch (DataIntegrityViolationException exception) {
            if (!isUniqueConstraintViolation(exception, REVIEW_HELPFUL_UNIQUE_CONSTRAINT)) {
                throw exception;
            }
            log.warn("이미 표시된 도움됐어요입니다 — reviewId={}, userId={}", review.getReviewId(), marker.getId());
            return;
        }

        reviewRepository.incrementHelpfulCount(review.getReviewId());

        // "구원자" 배지 — 도움됐어요는 작성자 본인의 행동이 아니라 남이 눌러주는 게 트리거라
        // GamificationService.grantXp의 XpEvent 경로를 안 탄다(그 클래스 참고). 총합은 이 도메인이
        // 이미 들고 있는 개념이라 여기서 계산해서 넘긴다.
        long totalHelpfulReceived = reviewRepository.sumHelpfulCountByUserId(author.getId());
        gamificationService.evaluateHelpfulSaviorBadge(author, totalHelpfulReceived);
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
