package com.freepets.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.review.converter.ReviewConverter;
import com.freepets.domain.review.dto.ReviewResponseDTO;
import com.freepets.domain.review.entity.Review;
import com.freepets.domain.review.entity.ReviewReport;
import com.freepets.domain.review.entity.ReviewReportReason;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.entity.Tag;
import com.freepets.domain.review.repository.ReviewReportRepository;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class ReviewQueryServiceTest {

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewReportRepository reviewReportRepository;

    @InjectMocks
    private ReviewQueryService reviewQueryService;

    private Facility createFacility(Long facilityId) {
        Facility facility = Facility.builder()
                .name("우리동네 카페")
                .category(FacilityCategory.CAFE)
                .address("서울시 강남구")
                .lat(BigDecimal.ONE)
                .lng(BigDecimal.ONE)
                .phone("02-1234-5678")
                .petAllowed(PetAllowed.ALLOWED)
                .maxWeight(BigDecimal.TEN)
                .contentId("12345")
                .petScore(80)
                .spaceRating(4.5f)
                .customerService(4.5f)
                .amenities(4.5f)
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

    private User createUser(Long userId) {
        User user = User.builder()
                .email("user" + userId + "@test.com")
                .passwordHash("encodedPassword")
                .nickname("몽이아빠")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Pet createPet(Long petId) {
        Pet pet = Pet.builder()
                .name("몽이")
                .kind(Kind.DOG)
                .species("말티즈")
                .weight(BigDecimal.valueOf(3.4))
                .breedSize(BreedSize.SMALL)
                .build();
        ReflectionTestUtils.setField(pet, "petId", petId);
        return pet;
    }

    private Review createReview(
            Long reviewId,
            Facility facility,
            User user,
            int ratingSpace,
            int ratingStaff,
            int ratingAmenity,
            List<Pet> pets,
            List<Tag> tags
    ) {
        Review review = Review.builder()
                .facility(facility)
                .user(user)
                .ratingSpace(ratingSpace)
                .ratingStaff(ratingStaff)
                .ratingAmenity(ratingAmenity)
                .content("좋았어요")
                .isShowPetInfo(true)
                .visitedAt(LocalDate.now())
                .build();
        ReflectionTestUtils.setField(review, "reviewId", reviewId);
        pets.forEach(pet -> review.getReviewPets().add(ReviewConverter.toReviewPet(review, pet)));
        tags.forEach(tag -> review.getTags().add(ReviewConverter.toReviewTag(review, tag)));
        return review;
    }

    @Test
    void getReviews_존재하지_않는_시설이면_예외를_던진다() {
        when(facilityRepository.existsById(7L)).thenReturn(false);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewQueryService.getReviews(7L, 1L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.FACILITY4041);
    }

    @Test
    void getReviews_리뷰가_없으면_집계_전_기본값을_반환한다() {
        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(reviewRepository.findAllByFacilityFacilityId(7L)).thenReturn(List.of());
        when(reviewReportRepository.findAllByStatusAndReviewFacilityFacilityId(ReviewReportStatus.APPROVED, 7L))
                .thenReturn(List.of());
        when(reviewReportRepository.findAllByUserIdAndReviewReviewIdIn(1L, List.of())).thenReturn(List.of());

        ReviewResponseDTO.ReviewListResult result = reviewQueryService.getReviews(7L, 1L);

        assertThat(result.grade().level()).isEqualTo(0);
        assertThat(result.grade().count()).isEqualTo(0);
        assertThat(result.grade().label()).isEqualTo("리뷰 수집 중 (0/10)");
        assertThat(result.grade().needMore()).isEqualTo(10);
        assertThat(result.reviews()).isEmpty();
    }

    @Test
    void getReviews_review_pets_단위로_가중_집계한다() {
        Facility facility = createFacility(7L);
        User author = createUser(100L);

        // 2마리 동반 리뷰: 공간4, 직원5, 편의3 -> score100 = round(avg(4,5,3)*20) = 80
        Review review1 = createReview(
                7001L, facility, author, 4, 5, 3,
                List.of(createPet(1L), createPet(2L)),
                List.of(Tag.LARGE_SPACE)
        );
        // 1마리 동반 리뷰: 공간5, 직원5, 편의5 -> score100 = 100
        Review review2 = createReview(
                7002L, facility, author, 5, 5, 5,
                List.of(createPet(3L)),
                List.of(Tag.LARGE_SPACE, Tag.WATER_EXIST)
        );

        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(reviewRepository.findAllByFacilityFacilityId(7L)).thenReturn(List.of(review1, review2));
        when(reviewReportRepository.findAllByStatusAndReviewFacilityFacilityId(ReviewReportStatus.APPROVED, 7L))
                .thenReturn(List.of());
        when(reviewReportRepository.findAllByUserIdAndReviewReviewIdIn(1L, List.of(7001L, 7002L)))
                .thenReturn(List.of());

        ReviewResponseDTO.ReviewListResult result = reviewQueryService.getReviews(7L, 1L);

        // count = 2(review1) + 1(review2) = 3
        assertThat(result.grade().count()).isEqualTo(3);
        // score = (80*2 + 100*1) / 3 = 86.7
        assertThat(result.grade().score()).isEqualTo(86.7);
        // 점수는 레벨1 기준(60점)을 넘지만 리뷰 수(3건)가 레벨1 최소 표본(10건)에 못 미쳐 등급 미부여
        assertThat(result.grade().level()).isEqualTo(0);
        assertThat(result.grade().label()).isEqualTo("리뷰 수집 중 (3/10)");
        assertThat(result.grade().needMore()).isEqualTo(7);

        // space = (4*2 + 5*1)/3 = 4.3, staff = (5*2+5*1)/3 = 5.0, amenity = (3*2+5*1)/3 = 3.7
        assertThat(result.categoryAverages().space()).isEqualTo(4.3);
        assertThat(result.categoryAverages().staff()).isEqualTo(5.0);
        assertThat(result.categoryAverages().amenity()).isEqualTo(3.7);

        // LARGE_SPACE: 2(review1) + 1(review2) = 3, WATER_EXIST: 1(review2)
        assertThat(result.topTags()).hasSize(2);
        assertThat(result.topTags().get(0).tag()).isEqualTo(Tag.LARGE_SPACE);
        assertThat(result.topTags().get(0).count()).isEqualTo(3);
        assertThat(result.topTags().get(1).tag()).isEqualTo(Tag.WATER_EXIST);
        assertThat(result.topTags().get(1).count()).isEqualTo(1);

        assertThat(result.reviews()).hasSize(2);
    }

    @Test
    void getReviews_점수와_리뷰수_기준을_모두_만족해야_등급이_부여된다() {
        Facility facility = createFacility(7L);
        User author = createUser(100L);

        List<Pet> group1 = List.of(createPet(1L), createPet(2L), createPet(3L), createPet(4L), createPet(5L));
        List<Pet> group2 = List.of(createPet(6L), createPet(7L), createPet(8L), createPet(9L), createPet(10L));

        // 리뷰 2건, 각각 5마리씩 -> review_pets 10건, score100 = round(avg(4,4,4)*20) = 80
        Review review1 = createReview(7001L, facility, author, 4, 4, 4, group1, List.of());
        Review review2 = createReview(7002L, facility, author, 4, 4, 4, group2, List.of());

        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(reviewRepository.findAllByFacilityFacilityId(7L)).thenReturn(List.of(review1, review2));
        when(reviewReportRepository.findAllByStatusAndReviewFacilityFacilityId(ReviewReportStatus.APPROVED, 7L))
                .thenReturn(List.of());
        when(reviewReportRepository.findAllByUserIdAndReviewReviewIdIn(1L, List.of(7001L, 7002L)))
                .thenReturn(List.of());

        ReviewResponseDTO.ReviewListResult result = reviewQueryService.getReviews(7L, 1L);

        // count=10, score=80 -> 레벨1(60점/10건)은 충족하지만 레벨2(70점/25건)는 리뷰 수 부족이라 레벨1에 머무름
        assertThat(result.grade().count()).isEqualTo(10);
        assertThat(result.grade().score()).isEqualTo(80.0);
        assertThat(result.grade().level()).isEqualTo(1);
        assertThat(result.grade().label()).isEqualTo("동반 가능");
        assertThat(result.grade().needMore()).isEqualTo(0);
    }

    @Test
    void getReviews_승인된_신고는_집계에서_제외되지만_목록에는_남는다() {
        Facility facility = createFacility(7L);
        User author = createUser(100L);

        Review reportedReview = createReview(
                7001L, facility, author, 5, 5, 5,
                List.of(createPet(1L)),
                List.of(Tag.LARGE_SPACE)
        );
        Review normalReview = createReview(
                7002L, facility, author, 3, 3, 3,
                List.of(createPet(2L)),
                List.of()
        );

        ReviewReport approvedReport = ReviewReport.builder()
                .review(reportedReview)
                .user(author)
                .reason(ReviewReportReason.SPAM)
                .status(ReviewReportStatus.APPROVED)
                .build();

        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(reviewRepository.findAllByFacilityFacilityId(7L)).thenReturn(List.of(reportedReview, normalReview));
        when(reviewReportRepository.findAllByStatusAndReviewFacilityFacilityId(ReviewReportStatus.APPROVED, 7L))
                .thenReturn(List.of(approvedReport));
        when(reviewReportRepository.findAllByUserIdAndReviewReviewIdIn(1L, List.of(7001L, 7002L)))
                .thenReturn(List.of());

        ReviewResponseDTO.ReviewListResult result = reviewQueryService.getReviews(7L, 1L);

        // 집계에는 normalReview(1마리, score100=60)만 반영됨
        assertThat(result.grade().count()).isEqualTo(1);
        assertThat(result.grade().score()).isEqualTo(60.0);
        // 목록에는 신고된 리뷰도 그대로 남음
        assertThat(result.reviews()).hasSize(2);
    }

    @Test
    void getReviews_내가_신고한_리뷰는_reportedByMe가_true다() {
        Facility facility = createFacility(7L);
        User author = createUser(100L);
        User viewer = createUser(1L);

        Review review = createReview(
                7001L, facility, author, 5, 5, 5,
                List.of(createPet(1L)),
                List.of()
        );

        ReviewReport myReport = ReviewReport.builder()
                .review(review)
                .user(viewer)
                .reason(ReviewReportReason.SPAM)
                .status(ReviewReportStatus.PENDING)
                .build();

        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(reviewRepository.findAllByFacilityFacilityId(7L)).thenReturn(List.of(review));
        when(reviewReportRepository.findAllByStatusAndReviewFacilityFacilityId(ReviewReportStatus.APPROVED, 7L))
                .thenReturn(List.of());
        when(reviewReportRepository.findAllByUserIdAndReviewReviewIdIn(1L, List.of(7001L)))
                .thenReturn(List.of(myReport));

        ReviewResponseDTO.ReviewListResult result = reviewQueryService.getReviews(7L, 1L);

        assertThat(result.reviews().get(0).reportedByMe()).isTrue();
    }
}
