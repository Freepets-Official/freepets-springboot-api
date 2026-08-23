package com.freepets.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

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
        review.replacePets(pets);
        review.replaceTags(tags);
        return review;
    }

    @Test
    void getReviews_존재하지_않는_시설이면_예외를_던진다() {
        when(facilityRepository.existsById(7L)).thenReturn(false);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewQueryService.getReviews(7L, 1L, 0)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.FACILITY4041);
    }

    @Test
    void getReviews_리뷰가_없으면_집계_전_기본값을_반환한다() {
        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(reviewRepository.findAllByFacilityFacilityIdAndDeletedAtIsNullOrderByCreatedAtDescReviewIdDesc(7L)).thenReturn(List.of());
        when(reviewReportRepository.findAllByStatusAndReviewFacilityFacilityId(ReviewReportStatus.ACCEPTED, 7L))
                .thenReturn(List.of());
        when(reviewReportRepository.findAllByUserIdAndReviewReviewIdIn(1L, List.of())).thenReturn(List.of());

        ReviewResponseDTO.ReviewListResult result = reviewQueryService.getReviews(7L, 1L, 0);

        assertThat(result.grade().level()).isEqualTo(0);
        assertThat(result.grade().count()).isEqualTo(0);
        assertThat(result.grade().label()).isEqualTo("리뷰 수집 중 (0/10)");
        assertThat(result.grade().needMore()).isEqualTo(10);
        assertThat(result.reviews()).isEmpty();
    }

    @Test
    void getReviews_다중_반려동물_리뷰도_리뷰_1건으로만_집계한다() {
        Facility facility = createFacility(7L);
        User author = createUser(100L);

        // 2마리를 같이 남긴 리뷰: 공간4, 직원5, 편의3 -> score100 = round((4*0.35+5*0.35+3*0.30)/5*100) = 81
        Review review1 = createReview(
                7001L, facility, author, 4, 5, 3,
                List.of(createPet(1L), createPet(2L)),
                List.of(Tag.SPACIOUS)
        );
        // 1마리 리뷰: 공간5, 직원5, 편의5 -> score100 = 100
        Review review2 = createReview(
                7002L, facility, author, 5, 5, 5,
                List.of(createPet(3L)),
                List.of(Tag.SPACIOUS, Tag.WATER_BOWL)
        );

        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(reviewRepository.findAllByFacilityFacilityIdAndDeletedAtIsNullOrderByCreatedAtDescReviewIdDesc(7L)).thenReturn(List.of(review1, review2));
        when(reviewReportRepository.findAllByStatusAndReviewFacilityFacilityId(ReviewReportStatus.ACCEPTED, 7L))
                .thenReturn(List.of());
        when(reviewReportRepository.findAllByUserIdAndReviewReviewIdIn(1L, List.of(7001L, 7002L)))
                .thenReturn(List.of());

        ReviewResponseDTO.ReviewListResult result = reviewQueryService.getReviews(7L, 1L, 0);

        // count는 review_pets(3마리)가 아니라 리뷰 건수 그대로 -> 2건 (review1이 2마리를 포함해도 1건)
        assertThat(result.grade().count()).isEqualTo(2);
        // score = avg(81, 100) = 90.5 (review1이 2마리라고 가중되지 않음)
        assertThat(result.grade().score()).isEqualTo(90.5);
        assertThat(result.grade().level()).isEqualTo(0);
        assertThat(result.grade().label()).isEqualTo("리뷰 수집 중 (2/10)");
        assertThat(result.grade().needMore()).isEqualTo(8);

        assertThat(result.categoryAverages().space()).isEqualTo(4.5);
        assertThat(result.categoryAverages().staff()).isEqualTo(5.0);
        assertThat(result.categoryAverages().amenity()).isEqualTo(4.0);

        // SPACIOUS: review1, review2 각각 1건씩 -> 2 (review1의 반려동물 2마리와 무관하게 1로만 카운트)
        assertThat(result.topTags()).hasSize(2);
        assertThat(result.topTags().get(0).tag()).isEqualTo(Tag.SPACIOUS);
        assertThat(result.topTags().get(0).count()).isEqualTo(2);
        assertThat(result.topTags().get(1).tag()).isEqualTo(Tag.WATER_BOWL);
        assertThat(result.topTags().get(1).count()).isEqualTo(1);

        assertThat(result.reviews()).hasSize(2);
        assertThat(result.reviews().get(0).pets()).hasSize(2);
    }

    @Test
    void getReviews_점수와_리뷰수_기준을_모두_만족해야_등급이_부여된다() {
        Facility facility = createFacility(7L);
        User author = createUser(100L);

        // 리뷰 10건(각 1마리), 공간·직원·편의 전부 3점 -> score100 = round((3*0.35+3*0.35+3*0.30)/5*100) = 60
        List<Review> reviews = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> createReview(7000L + i, facility, author, 3, 3, 3, List.of(createPet((long) i)), List.of()))
                .toList();
        List<Long> reviewIds = reviews.stream().map(Review::getReviewId).toList();

        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(reviewRepository.findAllByFacilityFacilityIdAndDeletedAtIsNullOrderByCreatedAtDescReviewIdDesc(7L)).thenReturn(reviews);
        when(reviewReportRepository.findAllByStatusAndReviewFacilityFacilityId(ReviewReportStatus.ACCEPTED, 7L))
                .thenReturn(List.of());
        when(reviewReportRepository.findAllByUserIdAndReviewReviewIdIn(1L, reviewIds))
                .thenReturn(List.of());

        ReviewResponseDTO.ReviewListResult result = reviewQueryService.getReviews(7L, 1L, 0);

        // count=10, score=60 -> 레벨1(60점/10건) 정확히 충족
        assertThat(result.grade().count()).isEqualTo(10);
        assertThat(result.grade().score()).isEqualTo(60.0);
        assertThat(result.grade().level()).isEqualTo(1);
        assertThat(result.grade().label()).isEqualTo("동반 가능");
        assertThat(result.grade().needMore()).isEqualTo(0);
    }

    @Test
    void getReviews_승인된_신고는_집계에서_제외되지만_목록에는_남는다() {
        Facility facility = createFacility(7L);
        User author = createUser(100L);

        Review reportedReview = createReview(7001L, facility, author, 5, 5, 5, List.of(createPet(1L)), List.of(Tag.SPACIOUS));
        Review normalReview = createReview(7002L, facility, author, 3, 3, 3, List.of(createPet(2L)), List.of());

        ReviewReport acceptedReport = ReviewReport.builder()
                .review(reportedReview)
                .user(author)
                .reason(ReviewReportReason.SPAM)
                .status(ReviewReportStatus.ACCEPTED)
                .build();

        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(reviewRepository.findAllByFacilityFacilityIdAndDeletedAtIsNullOrderByCreatedAtDescReviewIdDesc(7L)).thenReturn(List.of(reportedReview, normalReview));
        when(reviewReportRepository.findAllByStatusAndReviewFacilityFacilityId(ReviewReportStatus.ACCEPTED, 7L))
                .thenReturn(List.of(acceptedReport));
        when(reviewReportRepository.findAllByUserIdAndReviewReviewIdIn(1L, List.of(7001L, 7002L)))
                .thenReturn(List.of());

        ReviewResponseDTO.ReviewListResult result = reviewQueryService.getReviews(7L, 1L, 0);

        // 집계에는 normalReview(score100=60)만 반영됨
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

        Review review = createReview(7001L, facility, author, 5, 5, 5, List.of(createPet(1L)), List.of());

        ReviewReport myReport = ReviewReport.builder()
                .review(review)
                .user(viewer)
                .reason(ReviewReportReason.SPAM)
                .status(ReviewReportStatus.PENDING)
                .build();

        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(reviewRepository.findAllByFacilityFacilityIdAndDeletedAtIsNullOrderByCreatedAtDescReviewIdDesc(7L)).thenReturn(List.of(review));
        when(reviewReportRepository.findAllByStatusAndReviewFacilityFacilityId(ReviewReportStatus.ACCEPTED, 7L))
                .thenReturn(List.of());
        when(reviewReportRepository.findAllByUserIdAndReviewReviewIdIn(1L, List.of(7001L)))
                .thenReturn(List.of(myReport));

        ReviewResponseDTO.ReviewListResult result = reviewQueryService.getReviews(7L, 1L, 0);

        assertThat(result.reviews().get(0).reportedByMe()).isTrue();
    }

    @Test
    void getReviews_showPetInfo가_false면_반려동물_정보를_내려주지_않는다() {
        Facility facility = createFacility(7L);
        User author = createUser(100L);

        Review review = Review.builder()
                .facility(facility)
                .user(author)
                .ratingSpace(5)
                .ratingStaff(5)
                .ratingAmenity(5)
                .content("좋았어요")
                .isShowPetInfo(false)
                .visitedAt(LocalDate.now())
                .build();
        ReflectionTestUtils.setField(review, "reviewId", 7001L);
        review.replacePets(List.of(createPet(1L)));

        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(reviewRepository.findAllByFacilityFacilityIdAndDeletedAtIsNullOrderByCreatedAtDescReviewIdDesc(7L)).thenReturn(List.of(review));
        when(reviewReportRepository.findAllByStatusAndReviewFacilityFacilityId(ReviewReportStatus.ACCEPTED, 7L))
                .thenReturn(List.of());
        when(reviewReportRepository.findAllByUserIdAndReviewReviewIdIn(1L, List.of(7001L)))
                .thenReturn(List.of());

        ReviewResponseDTO.ReviewListResult result = reviewQueryService.getReviews(7L, 1L, 0);

        // isShowPetInfo=false면 실제로 붙어있는 반려동물이 있어도 응답 자체에서 빠져야 한다
        // (클라이언트가 플래그만 보고 화면에서만 가리는 방식이 되면 안 됨).
        assertThat(result.reviews().get(0).isShowPetInfo()).isFalse();
        assertThat(result.reviews().get(0).pets()).isEmpty();
    }

    @Test
    void getReviews_리뷰가_10건_넘으면_페이지당_10건씩만_내려주고_등급집계는_전체를_반영한다() {
        Facility facility = createFacility(7L);
        User author = createUser(100L);

        // 12건 전부 등급 집계에는 반영되지만, 화면 목록은 페이지당 10건으로 잘라야 한다.
        List<Review> reviews = IntStream.rangeClosed(1, 12)
                .mapToObj(i -> createReview(7000L + i, facility, author, 5, 5, 5, List.of(createPet((long) i)), List.of()))
                .toList();
        List<Long> reviewIds = reviews.stream().map(Review::getReviewId).toList();

        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(reviewRepository.findAllByFacilityFacilityIdAndDeletedAtIsNullOrderByCreatedAtDescReviewIdDesc(7L))
                .thenReturn(reviews);
        when(reviewReportRepository.findAllByStatusAndReviewFacilityFacilityId(ReviewReportStatus.ACCEPTED, 7L))
                .thenReturn(List.of());
        when(reviewReportRepository.findAllByUserIdAndReviewReviewIdIn(1L, reviewIds))
                .thenReturn(List.of());

        ReviewResponseDTO.ReviewListResult firstPage = reviewQueryService.getReviews(7L, 1L, 0);

        assertThat(firstPage.reviews()).hasSize(10);
        assertThat(firstPage.pageInfo().page()).isEqualTo(0);
        assertThat(firstPage.pageInfo().size()).isEqualTo(10);
        assertThat(firstPage.pageInfo().totalElements()).isEqualTo(12);
        assertThat(firstPage.pageInfo().hasNext()).isTrue();
        // 등급 집계(count)는 페이지 크기와 무관하게 12건 전부를 반영해야 한다.
        assertThat(firstPage.grade().count()).isEqualTo(12);

        ReviewResponseDTO.ReviewListResult secondPage = reviewQueryService.getReviews(7L, 1L, 1);

        assertThat(secondPage.reviews()).hasSize(2);
        assertThat(secondPage.pageInfo().hasNext()).isFalse();
    }

    @Test
    void getReviews_음수_페이지는_0페이지로_취급한다() {
        Facility facility = createFacility(7L);
        User author = createUser(100L);
        Review review = createReview(7001L, facility, author, 5, 5, 5, List.of(createPet(1L)), List.of());

        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(reviewRepository.findAllByFacilityFacilityIdAndDeletedAtIsNullOrderByCreatedAtDescReviewIdDesc(7L))
                .thenReturn(List.of(review));
        when(reviewReportRepository.findAllByStatusAndReviewFacilityFacilityId(ReviewReportStatus.ACCEPTED, 7L))
                .thenReturn(List.of());
        when(reviewReportRepository.findAllByUserIdAndReviewReviewIdIn(1L, List.of(7001L)))
                .thenReturn(List.of());

        ReviewResponseDTO.ReviewListResult result = reviewQueryService.getReviews(7L, 1L, -5);

        assertThat(result.pageInfo().page()).isEqualTo(0);
        assertThat(result.reviews()).hasSize(1);
    }
}
