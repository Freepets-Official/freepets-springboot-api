package com.freepets.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.review.converter.ReviewConverter;
import com.freepets.domain.review.dto.ReviewRequestDTO;
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
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class ReviewCommandServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewReportRepository reviewReportRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PetRepository petRepository;

    @Mock
    private PetCheckRepository petCheckRepository;

    @InjectMocks
    private ReviewCommandService reviewCommandService;

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

    private ReviewRequestDTO.UpsertRequest createUpsertRequest(List<Long> petIds) {
        ReviewRequestDTO.UpsertRequest request = new ReviewRequestDTO.UpsertRequest();
        request.setPetIds(petIds);
        request.setShowPetInfo(true);
        request.setRatingSpace(5);
        request.setRatingStaff(4);
        request.setRatingAmenity(5);
        request.setContent("좋았어요");
        request.setTags(List.of(Tag.LARGE_SPACE, Tag.WATER_EXIST));
        return request;
    }

    @Test
    void upsertReview_신규_리뷰를_생성한다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet1 = createPet(1L);
        Pet pet2 = createPet(2L);
        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L, 2L));

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petCheckRepository.existsByPetPetIdAndFacilityFacilityId(anyLong(), anyLong())).thenReturn(true);
        when(reviewRepository.findByFacilityFacilityIdAndUserId(7L, 1L)).thenReturn(Optional.empty());
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet1));
        when(petRepository.findByPetIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(pet2));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponseDTO.UpsertResult result = reviewCommandService.upsertReview(1L, 7L, request);

        assertThat(result.petIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(result.ratingSpace()).isEqualTo(5);
        assertThat(result.tags()).containsExactlyInAnyOrder(Tag.LARGE_SPACE, Tag.WATER_EXIST);
    }

    @Test
    void upsertReview_판별_이력_없는_반려동물이_있으면_예외를_던진다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L, 2L));

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petCheckRepository.existsByPetPetIdAndFacilityFacilityId(1L, 7L)).thenReturn(true);
        when(petCheckRepository.existsByPetPetIdAndFacilityFacilityId(2L, 7L)).thenReturn(false);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewCommandService.upsertReview(1L, 7L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4001);
        assertThat(exception.getResult()).isEqualTo(Map.of("ineligiblePetIds", List.of(2L)));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void upsertReview_기존_리뷰가_있으면_반려동물과_태그를_교체한다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet oldPet = createPet(1L);
        Pet newPet = createPet(2L);

        Review existingReview = Review.builder()
                .facility(facility)
                .user(user)
                .ratingSpace(3)
                .ratingStaff(3)
                .ratingAmenity(3)
                .content("예전 리뷰")
                .isShowPetInfo(false)
                .visitedAt(LocalDate.now().minusDays(10))
                .build();
        existingReview.getReviewPets().add(ReviewConverter.toReviewPet(existingReview, oldPet));
        existingReview.getTags().add(ReviewConverter.toReviewTag(existingReview, Tag.QUIET_ATMOSPHERE));

        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(2L));

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petCheckRepository.existsByPetPetIdAndFacilityFacilityId(2L, 7L)).thenReturn(true);
        when(reviewRepository.findByFacilityFacilityIdAndUserId(7L, 1L)).thenReturn(Optional.of(existingReview));
        when(petRepository.findByPetIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(newPet));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponseDTO.UpsertResult result = reviewCommandService.upsertReview(1L, 7L, request);

        assertThat(result.petIds()).containsExactly(2L);
        assertThat(result.ratingSpace()).isEqualTo(5);
        assertThat(existingReview.getReviewPets()).hasSize(1);
        assertThat(existingReview.getTags()).hasSize(2);
    }

    @Test
    void deleteReview_본인_리뷰면_삭제한다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Review review = Review.builder()
                .facility(facility)
                .user(user)
                .ratingSpace(5)
                .ratingStaff(5)
                .ratingAmenity(5)
                .content("좋았어요")
                .isShowPetInfo(true)
                .visitedAt(LocalDate.now())
                .build();
        ReflectionTestUtils.setField(review, "reviewId", 7001L);

        when(reviewRepository.findById(7001L)).thenReturn(Optional.of(review));

        ReviewResponseDTO.DeleteResult result = reviewCommandService.deleteReview(1L, 7001L);

        assertThat(result.reviewId()).isEqualTo(7001L);
        verify(reviewRepository).delete(review);
    }

    @Test
    void deleteReview_본인_리뷰가_아니면_예외를_던진다() {
        Facility facility = createFacility(7L);
        User owner = createUser(1L);
        Review review = Review.builder()
                .facility(facility)
                .user(owner)
                .ratingSpace(5)
                .ratingStaff(5)
                .ratingAmenity(5)
                .content("좋았어요")
                .isShowPetInfo(true)
                .visitedAt(LocalDate.now())
                .build();
        ReflectionTestUtils.setField(review, "reviewId", 7001L);

        when(reviewRepository.findById(7001L)).thenReturn(Optional.of(review));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewCommandService.deleteReview(2L, 7001L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4002);
        verify(reviewRepository, never()).delete(any());
    }

    @Test
    void deleteReview_존재하지_않으면_예외를_던진다() {
        when(reviewRepository.findById(7001L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewCommandService.deleteReview(1L, 7001L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4041);
    }

    @Test
    void reportReview_성공하면_PENDING_상태로_신고를_생성한다() {
        Facility facility = createFacility(7L);
        User author = createUser(100L);
        User reporter = createUser(1L);
        Review review = Review.builder()
                .facility(facility)
                .user(author)
                .ratingSpace(5)
                .ratingStaff(5)
                .ratingAmenity(5)
                .content("좋았어요")
                .isShowPetInfo(true)
                .visitedAt(LocalDate.now())
                .build();
        ReflectionTestUtils.setField(review, "reviewId", 7001L);

        ReviewRequestDTO.ReportRequest request = new ReviewRequestDTO.ReportRequest();
        request.setReason(ReviewReportReason.SPAM);

        when(reviewRepository.findById(7001L)).thenReturn(Optional.of(review));
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reviewReportRepository.existsByReviewReviewIdAndUserId(7001L, 1L)).thenReturn(false);
        when(reviewReportRepository.save(any(ReviewReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponseDTO.ReportResult result = reviewCommandService.reportReview(1L, 7001L, request);

        assertThat(result.reviewId()).isEqualTo(7001L);

        ArgumentCaptor<ReviewReport> captor = ArgumentCaptor.forClass(ReviewReport.class);
        verify(reviewReportRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReviewReportStatus.PENDING);
        assertThat(captor.getValue().getReason()).isEqualTo(ReviewReportReason.SPAM);
    }

    @Test
    void reportReview_이미_신고했으면_예외를_던진다() {
        Review review = Review.builder()
                .facility(createFacility(7L))
                .user(createUser(100L))
                .ratingSpace(5)
                .ratingStaff(5)
                .ratingAmenity(5)
                .content("좋았어요")
                .isShowPetInfo(true)
                .visitedAt(LocalDate.now())
                .build();
        ReflectionTestUtils.setField(review, "reviewId", 7001L);

        ReviewRequestDTO.ReportRequest request = new ReviewRequestDTO.ReportRequest();
        request.setReason(ReviewReportReason.SPAM);

        when(reviewRepository.findById(7001L)).thenReturn(Optional.of(review));
        when(userRepository.findById(1L)).thenReturn(Optional.of(createUser(1L)));
        when(reviewReportRepository.existsByReviewReviewIdAndUserId(7001L, 1L)).thenReturn(true);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewCommandService.reportReview(1L, 7001L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4003);
        verify(reviewReportRepository, never()).save(any());
    }

    @Test
    void reportReview_존재하지_않는_리뷰면_예외를_던진다() {
        ReviewRequestDTO.ReportRequest request = new ReviewRequestDTO.ReportRequest();
        request.setReason(ReviewReportReason.SPAM);

        when(reviewRepository.findById(7001L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewCommandService.reportReview(1L, 7001L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4041);
    }
}
