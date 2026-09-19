package com.freepets.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.service.FacilityGradeCacheService;
import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.gamification.service.GamificationService;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.review.dto.ReviewRequestDTO;
import com.freepets.domain.review.dto.ReviewResponseDTO;
import com.freepets.domain.review.entity.Review;
import com.freepets.domain.review.entity.ReviewReport;
import com.freepets.domain.review.entity.ReviewReportReason;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.entity.Tag;
import com.freepets.domain.review.repository.ReviewHelpfulRepository;
import com.freepets.domain.review.repository.ReviewReportRepository;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.infra.s3.S3ImageService;

@ExtendWith(MockitoExtension.class)
class ReviewCommandServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewReportRepository reviewReportRepository;

    @Mock
    private ReviewHelpfulRepository reviewHelpfulRepository;

    @Mock
    private ReviewHelpfulRecorder reviewHelpfulRecorder;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PetRepository petRepository;

    @Mock
    private PetCheckRepository petCheckRepository;

    @Mock
    private FacilityGradeCacheService facilityGradeCacheService;

    @Mock
    private GamificationService gamificationService;

    @Mock
    private S3ImageService s3ImageService;

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
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
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

    private Pet createPet(Long petId, User owner) {
        Pet pet = Pet.builder()
                .user(owner)
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
        request.setTags(List.of(Tag.SPACIOUS, Tag.WATER_BOWL));
        return request;
    }

    @Test
    void upsertReview_신규_리뷰를_생성한다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet1 = createPet(1L, user);
        Pet pet2 = createPet(2L, user);
        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L, 2L));

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petCheckRepository.existsByUserIdAndFacilityFacilityId(1L, 7L)).thenReturn(true);
        when(reviewRepository.findByFacilityFacilityIdAndUserIdAndDeletedAtIsNull(7L, 1L)).thenReturn(Optional.empty());
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet1));
        when(petRepository.findByPetIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(pet2));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponseDTO.UpsertResult result = reviewCommandService.upsertReview(1L, 7L, request);

        assertThat(result.petIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(result.ratingSpace()).isEqualTo(5);
        assertThat(result.tags()).containsExactlyInAnyOrder(Tag.SPACIOUS, Tag.WATER_BOWL);
        // 신규 작성에만 경험치가 지급되는지(게이미피케이션 훅) — 태그된 반려동물 각자에게도 지급.
        verify(gamificationService).grantXp(eq(1L), eq(XpSourceType.REVIEW), any(), eq(20), eq(List.of(pet1, pet2)));
    }

    @Test
    void upsertReview_petIds와_tags에_중복이_있으면_한_번만_반영한다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet = createPet(1L, user);
        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L, 1L, 1L));
        request.setTags(List.of(Tag.SPACIOUS, Tag.SPACIOUS, Tag.WATER_BOWL));

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petCheckRepository.existsByUserIdAndFacilityFacilityId(1L, 7L)).thenReturn(true);
        when(reviewRepository.findByFacilityFacilityIdAndUserIdAndDeletedAtIsNull(7L, 1L)).thenReturn(Optional.empty());
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponseDTO.UpsertResult result = reviewCommandService.upsertReview(1L, 7L, request);

        // 중복 petId만큼 소유자 검증 쿼리가 반복 실행되지 않아야 하고, 결과에도 중복이 남지 않아야 한다.
        assertThat(result.petIds()).containsExactly(1L);
        assertThat(result.tags()).containsExactlyInAnyOrder(Tag.SPACIOUS, Tag.WATER_BOWL);
        verify(petRepository, times(1)).findByPetIdAndDeletedAtIsNull(1L);
    }

    @Test
    void upsertReview_저장중_DB_유니크_제약에_걸리면_충돌_에러를_던진다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet = createPet(1L, user);
        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L));

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petCheckRepository.existsByUserIdAndFacilityFacilityId(1L, 7L)).thenReturn(true);
        when(reviewRepository.findByFacilityFacilityIdAndUserIdAndDeletedAtIsNull(7L, 1L)).thenReturn(Optional.empty());
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet));
        // 동시에 두 번 제출되면 둘 다 "기존 리뷰 없음"으로 보고 insert를 시도할 수 있는데,
        // DB의 부분 유니크 인덱스(시설+유저, 삭제되지 않은 리뷰)가 뒤늦은 쪽을 막아준다.
        ConstraintViolationException uniqueConstraintViolation = new ConstraintViolationException(
                "duplicate key value violates unique constraint",
                new SQLException("duplicate key"),
                "uq_reviews_facility_user_active"
        );
        when(reviewRepository.save(any(Review.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key", uniqueConstraintViolation));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewCommandService.upsertReview(1L, 7L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4004);
    }

    @Test
    void upsertReview_다른_원인의_무결성_위반이면_변환하지_않고_그대로_던진다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet = createPet(1L, user);
        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L));

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petCheckRepository.existsByUserIdAndFacilityFacilityId(1L, 7L)).thenReturn(true);
        when(reviewRepository.findByFacilityFacilityIdAndUserIdAndDeletedAtIsNull(7L, 1L)).thenReturn(Optional.empty());
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet));
        // 우리가 기대한 유니크 인덱스가 아닌 다른 무결성 위반(FK, not-null 등)까지 409로
        // 뭉뚱그리면 실제 원인을 놓치게 되므로, 이 경우엔 변환하지 않고 그대로 올려야 한다.
        DataIntegrityViolationException otherViolation = new DataIntegrityViolationException("not-null violation");
        when(reviewRepository.save(any(Review.class))).thenThrow(otherViolation);

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> reviewCommandService.upsertReview(1L, 7L, request)
        );

        assertThat(exception).isSameAs(otherViolation);
    }

    @Test
    void upsertReview_이_시설에서_판별_이력이_없으면_예외를_던진다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L, 2L));

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petCheckRepository.existsByUserIdAndFacilityFacilityId(1L, 7L)).thenReturn(false);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewCommandService.upsertReview(1L, 7L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4001);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void upsertReview_시설_판별_이력만_있으면_개별_판별_없는_반려동물도_포함할_수_있다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        // 새/토끼처럼 개별 AI 판별 자체가 없는 반려동물이라고 가정 — pet_checks에 이 pet_id는 없다.
        Pet bird = createPet(1L, user);
        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L));

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        // 시설 단위로는 판별 이력이 있다 (다른 반려동물로 판별받았을 수 있음)
        when(petCheckRepository.existsByUserIdAndFacilityFacilityId(1L, 7L)).thenReturn(true);
        when(reviewRepository.findByFacilityFacilityIdAndUserIdAndDeletedAtIsNull(7L, 1L)).thenReturn(Optional.empty());
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(bird));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponseDTO.UpsertResult result = reviewCommandService.upsertReview(1L, 7L, request);

        assertThat(result.petIds()).containsExactly(1L);
        // AI 판별 그룹 판별 재설계로 PetCheck에 pet_id가 아예 없어져서(PetCheckVerdict로 이동),
        // "펫 단위로는 확인 안 함"을 런타임 검증할 대상 자체가 사라졌다 — 엔티티 구조로 이미 보장됨.
    }

    @Test
    void upsertReview_기존_리뷰가_있으면_반려동물과_태그를_교체한다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet oldPet = createPet(1L, user);
        Pet newPet = createPet(2L, user);

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
        existingReview.replacePets(List.of(oldPet));
        existingReview.replaceTags(List.of(Tag.QUIET));

        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(2L));

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petCheckRepository.existsByUserIdAndFacilityFacilityId(1L, 7L)).thenReturn(true);
        when(reviewRepository.findByFacilityFacilityIdAndUserIdAndDeletedAtIsNull(7L, 1L)).thenReturn(Optional.of(existingReview));
        when(petRepository.findByPetIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(newPet));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponseDTO.UpsertResult result = reviewCommandService.upsertReview(1L, 7L, request);

        assertThat(result.petIds()).containsExactly(2L);
        assertThat(result.ratingSpace()).isEqualTo(5);
        assertThat(existingReview.getReviewPets()).hasSize(1);
        assertThat(existingReview.getTags()).hasSize(2);
        // 요청에 visitedAt을 안 보내면 기존 방문일을 그대로 유지해야 한다.
        assertThat(result.visitedAt()).isEqualTo(LocalDate.now().minusDays(10));
        // 수정은 경험치를 지급하지 않는다(게이미피케이션 결정).
        verifyNoInteractions(gamificationService);
    }

    @Test
    void upsertReview_신규_리뷰에_사진을_첨부하면_S3에_업로드하고_photoUrl로_내려준다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet = createPet(1L, user);
        MultipartFile photo = mock(MultipartFile.class);
        when(photo.isEmpty()).thenReturn(false);
        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L));
        request.setPhoto(photo);

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petCheckRepository.existsByUserIdAndFacilityFacilityId(1L, 7L)).thenReturn(true);
        when(reviewRepository.findByFacilityFacilityIdAndUserIdAndDeletedAtIsNull(7L, 1L)).thenReturn(Optional.empty());
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet));
        when(s3ImageService.upload(photo)).thenReturn("https://s3/new-photo.jpg");
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponseDTO.UpsertResult result = reviewCommandService.upsertReview(1L, 7L, request);

        assertThat(result.photoUrl()).isEqualTo("https://s3/new-photo.jpg");
        // 신규 작성이라 지울 이전 사진이 없다.
        verify(s3ImageService, never()).delete(any());
    }

    @Test
    void upsertReview_기존_리뷰의_사진을_교체하면_이전_사진을_S3에서_지운다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet = createPet(1L, user);

        Review existingReview = Review.builder()
                .facility(facility)
                .user(user)
                .ratingSpace(3)
                .ratingStaff(3)
                .ratingAmenity(3)
                .content("예전 리뷰")
                .isShowPetInfo(false)
                .visitedAt(LocalDate.now().minusDays(10))
                .photoUrl("https://s3/old-photo.jpg")
                .build();
        existingReview.replacePets(List.of(pet));

        MultipartFile newPhoto = mock(MultipartFile.class);
        when(newPhoto.isEmpty()).thenReturn(false);
        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L));
        request.setPhoto(newPhoto);

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petCheckRepository.existsByUserIdAndFacilityFacilityId(1L, 7L)).thenReturn(true);
        when(reviewRepository.findByFacilityFacilityIdAndUserIdAndDeletedAtIsNull(7L, 1L)).thenReturn(Optional.of(existingReview));
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet));
        when(s3ImageService.upload(newPhoto)).thenReturn("https://s3/new-photo.jpg");
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponseDTO.UpsertResult result = reviewCommandService.upsertReview(1L, 7L, request);

        assertThat(result.photoUrl()).isEqualTo("https://s3/new-photo.jpg");
        verify(s3ImageService).delete("https://s3/old-photo.jpg");
    }

    @Test
    void upsertReview_사진_없이_수정하면_기존_사진을_유지하고_지우지_않는다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet = createPet(1L, user);

        Review existingReview = Review.builder()
                .facility(facility)
                .user(user)
                .ratingSpace(3)
                .ratingStaff(3)
                .ratingAmenity(3)
                .content("예전 리뷰")
                .isShowPetInfo(false)
                .visitedAt(LocalDate.now().minusDays(10))
                .photoUrl("https://s3/old-photo.jpg")
                .build();
        existingReview.replacePets(List.of(pet));

        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L));

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petCheckRepository.existsByUserIdAndFacilityFacilityId(1L, 7L)).thenReturn(true);
        when(reviewRepository.findByFacilityFacilityIdAndUserIdAndDeletedAtIsNull(7L, 1L)).thenReturn(Optional.of(existingReview));
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponseDTO.UpsertResult result = reviewCommandService.upsertReview(1L, 7L, request);

        assertThat(result.photoUrl()).isEqualTo("https://s3/old-photo.jpg");
        verify(s3ImageService, never()).delete(any());
        verify(s3ImageService, never()).upload(any());
    }

    @Test
    void upsertReview_저장에_실패하면_새로_업로드한_사진을_S3에서_지운다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet = createPet(1L, user);
        MultipartFile photo = mock(MultipartFile.class);
        when(photo.isEmpty()).thenReturn(false);
        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L));
        request.setPhoto(photo);

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petCheckRepository.existsByUserIdAndFacilityFacilityId(1L, 7L)).thenReturn(true);
        when(reviewRepository.findByFacilityFacilityIdAndUserIdAndDeletedAtIsNull(7L, 1L)).thenReturn(Optional.empty());
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet));
        when(s3ImageService.upload(photo)).thenReturn("https://s3/orphan-photo.jpg");
        // 동시에 같은 시설+유저로 리뷰가 하나 더 저장돼 유니크 인덱스에 걸린 상황을 흉내낸다.
        ConstraintViolationException uniqueConstraintViolation = new ConstraintViolationException(
                "duplicate key value violates unique constraint",
                new SQLException("duplicate key"),
                "uq_reviews_facility_user_active"
        );
        when(reviewRepository.save(any(Review.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key", uniqueConstraintViolation));

        assertThrows(
                GeneralException.class,
                () -> reviewCommandService.upsertReview(1L, 7L, request)
        );

        // 저장이 실패했으니 방금 올린 사진이 고아 파일로 남지 않도록 지워야 한다.
        verify(s3ImageService).delete("https://s3/orphan-photo.jpg");
    }

    @Test
    void upsertReview_기존_리뷰_수정시_방문일을_요청에_담아도_바뀌지_않는다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet = createPet(1L, user);

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
        existingReview.replacePets(List.of(pet));

        // 방문일은 실제로 다녀온 날짜라 수정 화면에서 다른 값을 보내도 무시되어야 한다.
        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L));
        request.setVisitedAt(LocalDate.now());

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petCheckRepository.existsByUserIdAndFacilityFacilityId(1L, 7L)).thenReturn(true);
        when(reviewRepository.findByFacilityFacilityIdAndUserIdAndDeletedAtIsNull(7L, 1L)).thenReturn(Optional.of(existingReview));
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponseDTO.UpsertResult result = reviewCommandService.upsertReview(1L, 7L, request);

        assertThat(result.visitedAt()).isEqualTo(LocalDate.now().minusDays(10));
    }

    @Test
    void upsertReview_다른_사용자의_반려동물이면_예외를_던진다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        User strangerOwner = createUser(2L);
        Pet strangerPet = createPet(1L, strangerOwner);
        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L));

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petCheckRepository.existsByUserIdAndFacilityFacilityId(1L, 7L)).thenReturn(true);
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(strangerPet));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewCommandService.upsertReview(1L, 7L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.PET4002);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void updateReview_reviewId로_직접_수정하면_방문일은_유지된채_나머지가_바뀐다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet oldPet = createPet(1L, user);
        Pet newPet = createPet(2L, user);

        Review review = Review.builder()
                .facility(facility)
                .user(user)
                .ratingSpace(3)
                .ratingStaff(3)
                .ratingAmenity(3)
                .content("예전 리뷰")
                .isShowPetInfo(false)
                .visitedAt(LocalDate.now().minusDays(10))
                .build();
        review.replacePets(List.of(oldPet));
        review.replaceTags(List.of(Tag.QUIET));
        ReflectionTestUtils.setField(review, "reviewId", 7001L);

        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(2L));

        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.of(review));
        when(petRepository.findByPetIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(newPet));

        ReviewResponseDTO.UpsertResult result = reviewCommandService.updateReview(1L, 7001L, request);

        assertThat(result.reviewId()).isEqualTo(7001L);
        assertThat(result.petIds()).containsExactly(2L);
        assertThat(result.ratingSpace()).isEqualTo(5);
        assertThat(result.visitedAt()).isEqualTo(LocalDate.now().minusDays(10));
        verify(facilityGradeCacheService).refresh(7L);
        // 새 리뷰가 아니라 경험치는 지급되지 않는다.
        verifyNoInteractions(gamificationService);
    }

    @Test
    void updateReview_사진을_교체하면_이전_사진을_S3에서_지운다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet = createPet(1L, user);

        Review review = Review.builder()
                .facility(facility)
                .user(user)
                .ratingSpace(3)
                .ratingStaff(3)
                .ratingAmenity(3)
                .content("예전 리뷰")
                .isShowPetInfo(false)
                .visitedAt(LocalDate.now().minusDays(10))
                .photoUrl("https://s3/old-photo.jpg")
                .build();
        review.replacePets(List.of(pet));
        ReflectionTestUtils.setField(review, "reviewId", 7001L);

        MultipartFile newPhoto = mock(MultipartFile.class);
        when(newPhoto.isEmpty()).thenReturn(false);
        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L));
        request.setPhoto(newPhoto);

        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.of(review));
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet));
        when(s3ImageService.upload(newPhoto)).thenReturn("https://s3/new-photo.jpg");

        ReviewResponseDTO.UpsertResult result = reviewCommandService.updateReview(1L, 7001L, request);

        assertThat(result.photoUrl()).isEqualTo("https://s3/new-photo.jpg");
        verify(s3ImageService).delete("https://s3/old-photo.jpg");
    }

    @Test
    void updateReview_사진_없이_수정하면_기존_사진을_유지하고_지우지_않는다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet = createPet(1L, user);

        Review review = Review.builder()
                .facility(facility)
                .user(user)
                .ratingSpace(3)
                .ratingStaff(3)
                .ratingAmenity(3)
                .content("예전 리뷰")
                .isShowPetInfo(false)
                .visitedAt(LocalDate.now().minusDays(10))
                .photoUrl("https://s3/old-photo.jpg")
                .build();
        review.replacePets(List.of(pet));
        ReflectionTestUtils.setField(review, "reviewId", 7001L);

        ReviewRequestDTO.UpsertRequest request = createUpsertRequest(List.of(1L));

        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.of(review));
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet));

        ReviewResponseDTO.UpsertResult result = reviewCommandService.updateReview(1L, 7001L, request);

        assertThat(result.photoUrl()).isEqualTo("https://s3/old-photo.jpg");
        verify(s3ImageService, never()).delete(any());
        verify(s3ImageService, never()).upload(any());
    }

    @Test
    void updateReview_본인_리뷰가_아니면_예외를_던진다() {
        Facility facility = createFacility(7L);
        Review review = Review.builder()
                .facility(facility)
                .user(createUser(1L))
                .ratingSpace(5)
                .ratingStaff(5)
                .ratingAmenity(5)
                .content("좋았어요")
                .isShowPetInfo(true)
                .visitedAt(LocalDate.now())
                .build();
        ReflectionTestUtils.setField(review, "reviewId", 7001L);

        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.of(review));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewCommandService.updateReview(2L, 7001L, createUpsertRequest(List.of(1L)))
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4002);
    }

    @Test
    void updateReview_존재하지_않으면_예외를_던진다() {
        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewCommandService.updateReview(1L, 7001L, createUpsertRequest(List.of(1L)))
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4041);
    }

    @Test
    void markHelpful_처음_표시하면_카운트가_1_증가한다() {
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
        // incrementHelpfulCount는 영속성 컨텍스트를 안 거치는 벌크 업데이트라, 최신 값은 재조회로만
        // 얻는다 — 재조회 시점에 DB가 이미 반영된 걸 흉내내려고 별도 인스턴스를 하나 더 둔다.
        Review refreshed = Review.builder()
                .facility(review.getFacility())
                .user(review.getUser())
                .ratingSpace(5)
                .ratingStaff(5)
                .ratingAmenity(5)
                .content("좋았어요")
                .isShowPetInfo(true)
                .visitedAt(LocalDate.now())
                .build();
        ReflectionTestUtils.setField(refreshed, "reviewId", 7001L);
        ReflectionTestUtils.setField(refreshed, "helpfulCount", 1L);
        User marker = createUser(1L);

        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L))
                .thenReturn(Optional.of(review))
                .thenReturn(Optional.of(refreshed));
        when(reviewHelpfulRepository.existsByReviewReviewIdAndUserId(7001L, 1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(marker));
        // 이 유저(작성자 100L)의 리뷰 전체가 받은 도움됐어요 총합 — "구원자" 배지 평가에 넘어간다.
        when(reviewRepository.sumHelpfulCountByUserId(100L)).thenReturn(7L);

        ReviewResponseDTO.HelpfulResult result = reviewCommandService.markHelpful(1L, 7001L);

        assertThat(result.helpfulCount()).isEqualTo(1L);
        verify(reviewHelpfulRecorder).record(review, marker);
        verify(reviewRepository).incrementHelpfulCount(7001L);
        // 도움됐어요 표시 자체는 작성자(리뷰 주인)에게 평가되는 배지다 — 누른 사람(marker)이 아니다.
        verify(gamificationService).evaluateHelpfulSaviorBadge(review.getUser(), 7L);
    }

    @Test
    void markHelpful_이미_표시했으면_중복으로_늘지_않는다() {
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

        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.of(review));
        when(reviewHelpfulRepository.existsByReviewReviewIdAndUserId(7001L, 1L)).thenReturn(true);

        ReviewResponseDTO.HelpfulResult result = reviewCommandService.markHelpful(1L, 7001L);

        assertThat(result.helpfulCount()).isEqualTo(0L);
        verify(reviewHelpfulRecorder, never()).record(any(), any());
        verify(reviewRepository, never()).incrementHelpfulCount(any());
        verify(userRepository, never()).findById(any());
        verify(gamificationService, never()).evaluateHelpfulSaviorBadge(any(), anyLong());
    }

    @Test
    void markHelpful_이미_표시된_경우_저장_시점에_유니크_제약_위반이_나도_조용히_넘어간다() {
        // exists 확인과 저장 사이에 거의 동시에 두 번 눌린 race — DB 유니크 제약이 뒤늦은 쪽을
        // 막아준다. ReviewHelpfulRecorder는 별도 트랜잭션이라 이 예외를 여기서 잡아도 호출부의
        // 트랜잭션(review 조회 등)에는 영향이 없다.
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
        User marker = createUser(1L);

        ConstraintViolationException uniqueConstraintViolation = new ConstraintViolationException(
                "duplicate key value violates unique constraint",
                new SQLException("duplicate key"),
                "uk_review_helpfuls_review_user"
        );

        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.of(review));
        when(reviewHelpfulRepository.existsByReviewReviewIdAndUserId(7001L, 1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(marker));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate key", uniqueConstraintViolation))
                .when(reviewHelpfulRecorder).record(review, marker);

        ReviewResponseDTO.HelpfulResult result = reviewCommandService.markHelpful(1L, 7001L);

        assertThat(result.helpfulCount()).isEqualTo(0L);
        verify(reviewRepository, never()).incrementHelpfulCount(any());
        verify(gamificationService, never()).evaluateHelpfulSaviorBadge(any(), anyLong());
    }

    @Test
    void markHelpful_본인_리뷰는_표시할_수_없다() {
        Review review = Review.builder()
                .facility(createFacility(7L))
                .user(createUser(1L))
                .ratingSpace(5)
                .ratingStaff(5)
                .ratingAmenity(5)
                .content("좋았어요")
                .isShowPetInfo(true)
                .visitedAt(LocalDate.now())
                .build();
        ReflectionTestUtils.setField(review, "reviewId", 7001L);

        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.of(review));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewCommandService.markHelpful(1L, 7001L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4005);
        verify(reviewHelpfulRecorder, never()).record(any(), any());
    }

    @Test
    void markHelpful_존재하지_않는_리뷰면_예외를_던진다() {
        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewCommandService.markHelpful(1L, 7001L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4041);
    }

    @Test
    void unmarkHelpful_표시돼_있으면_카운트가_1_감소한다() {
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
        ReflectionTestUtils.setField(review, "helpfulCount", 1L);
        // decrementHelpfulCount도 incrementHelpfulCount와 같은 벌크 업데이트라 재조회로만 최신
        // 값을 얻는다.
        Review refreshed = Review.builder()
                .facility(review.getFacility())
                .user(review.getUser())
                .ratingSpace(5)
                .ratingStaff(5)
                .ratingAmenity(5)
                .content("좋았어요")
                .isShowPetInfo(true)
                .visitedAt(LocalDate.now())
                .build();
        ReflectionTestUtils.setField(refreshed, "reviewId", 7001L);
        ReflectionTestUtils.setField(refreshed, "helpfulCount", 0L);

        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L))
                .thenReturn(Optional.of(review))
                .thenReturn(Optional.of(refreshed));
        when(reviewHelpfulRepository.deleteByReviewReviewIdAndUserId(7001L, 1L)).thenReturn(1);

        ReviewResponseDTO.HelpfulResult result = reviewCommandService.unmarkHelpful(1L, 7001L);

        assertThat(result.helpfulCount()).isEqualTo(0L);
        verify(reviewRepository).decrementHelpfulCount(7001L);
    }

    @Test
    void unmarkHelpful_표시한_적_없으면_아무것도_하지_않고_그대로_성공한다() {
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

        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.of(review));
        when(reviewHelpfulRepository.deleteByReviewReviewIdAndUserId(7001L, 1L)).thenReturn(0);

        ReviewResponseDTO.HelpfulResult result = reviewCommandService.unmarkHelpful(1L, 7001L);

        assertThat(result.helpfulCount()).isEqualTo(0L);
        verify(reviewRepository, never()).decrementHelpfulCount(any());
    }

    @Test
    void unmarkHelpful_존재하지_않는_리뷰면_예외를_던진다() {
        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewCommandService.unmarkHelpful(1L, 7001L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4041);
        verify(reviewHelpfulRepository, never()).deleteByReviewReviewIdAndUserId(any(), any());
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

        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.of(review));

        ReviewResponseDTO.DeleteResult result = reviewCommandService.deleteReview(1L, 7001L);

        assertThat(result.reviewId()).isEqualTo(7001L);
        // 신고 이력을 남겨야 해서 하드 삭제 대신 deletedAt만 채우는 소프트 삭제를 쓴다.
        assertThat(review.isDeleted()).isTrue();
        verify(reviewRepository, never()).delete(any());
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

        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.of(review));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewCommandService.deleteReview(2L, 7001L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4002);
        assertThat(review.isDeleted()).isFalse();
    }

    @Test
    void deleteReview_존재하지_않으면_예외를_던진다() {
        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.empty());

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

        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.of(review));
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

        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.of(review));
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

        when(reviewRepository.findByReviewIdAndDeletedAtIsNull(7001L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> reviewCommandService.reportReview(1L, 7001L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.REVIEW4041);
    }
}
