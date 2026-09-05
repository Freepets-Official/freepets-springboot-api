package com.freepets.domain.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.dto.FacilityRequestDTO;
import com.freepets.domain.facility.dto.FacilityResponseDTO;
import com.freepets.domain.facility.entity.Confidence;
import com.freepets.domain.facility.entity.ConfidenceSource;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Region;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.repository.FacilityWithDistance;
import com.freepets.domain.facility.repository.RegionRepository;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.report.repository.FacilityReportRepository;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.repository.FacilityReviewAggregate;
import com.freepets.domain.review.repository.FacilityReviewCount;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class FacilityQueryServiceTest {

    private static final double SEOUL_LATITUDE = 37.5665;
    private static final double SEOUL_LONGITUDE = 126.9780;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private PetRepository petRepository;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private FacilityReportRepository facilityReportRepository;

    @InjectMocks
    private FacilityQueryService facilityQueryService;

    private FacilityRequestDTO.SearchRequest createSearchRequest() {
        FacilityRequestDTO.SearchRequest request = new FacilityRequestDTO.SearchRequest();
        request.setLatitude(SEOUL_LATITUDE);
        request.setLongitude(SEOUL_LONGITUDE);
        return request;
    }

    private Facility createFacility(Long facilityId) {
        Facility facility = Facility.builder()
                .name("카페 파도살롱")
                .category(FacilityCategory.CAFE)
                .address("강원 강릉시 창해로 17")
                .lat(new BigDecimal("37.8000000"))
                .lng(new BigDecimal("128.9000000"))
                .petAllowed(PetAllowed.ALLOWED)
                .maxWeight(new BigDecimal("10.00"))
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();

        // facilityId는 DB가 채우는 값이라 빌더에 없다.
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        facility.replaceRequirements(List.of(Requirement.LEASH));

        return facility;
    }

    // ------------------------------------------------------------------
    // 반경 유무에 따른 쿼리 선택
    // ------------------------------------------------------------------

    @Test
    @DisplayName("반경이 없으면 경계 사각형 없이 조회한다")
    void 반경이_없으면_경계_사각형_없이_조회한다() {
        FacilityRequestDTO.SearchRequest request = createSearchRequest();

        when(facilityRepository.search(anyDouble(), anyDouble(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of());
        when(facilityRepository.countSearch(isNull(), isNull(), isNull())).thenReturn(0L);

        facilityQueryService.searchFacilities(request);

        verify(facilityRepository).search(anyDouble(), anyDouble(), isNull(), isNull(), isNull(), any(Pageable.class));
        verify(facilityRepository).countSearch(isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("반경이 있으면 경계 사각형과 반경을 함께 넘긴다")
    void 반경이_있으면_경계_사각형과_반경을_함께_넘긴다() {
        FacilityRequestDTO.SearchRequest request = createSearchRequest();
        request.setRadiusM(3000);

        when(facilityRepository.searchWithinRadius(
                anyDouble(), anyDouble(), isNull(), isNull(), isNull(),
                any(), any(), any(), any(), anyDouble(), any(Pageable.class)))
                .thenReturn(List.of());
        when(facilityRepository.countSearchWithinRadius(
                anyDouble(), anyDouble(), isNull(), isNull(), isNull(),
                any(), any(), any(), any(), anyDouble()))
                .thenReturn(0L);

        facilityQueryService.searchFacilities(request);

        ArgumentCaptor<BigDecimal> minimumLatitude = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> maximumLatitude = ArgumentCaptor.forClass(BigDecimal.class);

        verify(facilityRepository).searchWithinRadius(
                anyDouble(), anyDouble(), isNull(), isNull(), isNull(),
                minimumLatitude.capture(), maximumLatitude.capture(), any(), any(),
                eq(3000.0), any(Pageable.class));

        // 반경 3km는 위도로 약 0.027도다. 사용자 위치를 감싸야 한다.
        assertThat(minimumLatitude.getValue().doubleValue()).isLessThan(SEOUL_LATITUDE);
        assertThat(maximumLatitude.getValue().doubleValue()).isGreaterThan(SEOUL_LATITUDE);
    }

    // ------------------------------------------------------------------
    // 검색어 정규화
    // ------------------------------------------------------------------

    @Test
    @DisplayName("검색어를 소문자 부분 일치 패턴으로 바꾼다")
    void 검색어를_소문자_부분_일치_패턴으로_바꾼다() {
        FacilityRequestDTO.SearchRequest request = createSearchRequest();
        request.setKeyword("  PaDo  ");

        when(facilityRepository.search(anyDouble(), anyDouble(), any(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of());
        when(facilityRepository.countSearch(any(), isNull(), isNull())).thenReturn(0L);

        facilityQueryService.searchFacilities(request);

        verify(facilityRepository).countSearch(eq("%pado%"), isNull(), isNull());
    }

    @Test
    @DisplayName("검색어의 와일드카드 문자는 글자 그대로 찾도록 이스케이프한다")
    void 검색어의_와일드카드_문자는_글자_그대로_찾도록_이스케이프한다() {
        FacilityRequestDTO.SearchRequest request = createSearchRequest();
        request.setKeyword("100%_할인!");

        when(facilityRepository.search(anyDouble(), anyDouble(), any(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of());
        when(facilityRepository.countSearch(any(), isNull(), isNull())).thenReturn(0L);

        facilityQueryService.searchFacilities(request);

        verify(facilityRepository).countSearch(eq("%100!%!_할인!!%"), isNull(), isNull());
    }

    @Test
    @DisplayName("검색어가 공백뿐이면 조건에서 뺀다")
    void 검색어가_공백뿐이면_조건에서_뺀다() {
        FacilityRequestDTO.SearchRequest request = createSearchRequest();
        request.setKeyword("   ");

        when(facilityRepository.search(anyDouble(), anyDouble(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of());
        when(facilityRepository.countSearch(isNull(), isNull(), isNull())).thenReturn(0L);

        facilityQueryService.searchFacilities(request);

        verify(facilityRepository).countSearch(isNull(), isNull(), isNull());
    }

    // ------------------------------------------------------------------
    // 응답 조립
    // ------------------------------------------------------------------

    @Test
    @DisplayName("거리는 미터 단위 정수로 반올림하고 전체 건수를 함께 내려준다")
    void 거리는_미터_단위_정수로_반올림하고_전체_건수를_함께_내려준다() {
        FacilityRequestDTO.SearchRequest request = createSearchRequest();
        Facility facility = createFacility(2L);

        when(facilityRepository.search(anyDouble(), anyDouble(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(new FacilityWithDistance(facility, 1200.6)));
        when(facilityRepository.countSearch(isNull(), isNull(), isNull())).thenReturn(14L);
        when(reviewRepository.countByFacilityIds(List.of(2L)))
                .thenReturn(List.of(new FacilityReviewCount(2L, 45L)));

        FacilityResponseDTO.FacilitySearchResult result = facilityQueryService.searchFacilities(request);

        assertThat(result.total()).isEqualTo(14L);
        assertThat(result.items()).hasSize(1);

        FacilityResponseDTO.FacilitySummary summary = result.items().get(0);
        assertThat(summary.facilityId()).isEqualTo(2L);
        assertThat(summary.name()).isEqualTo("카페 파도살롱");
        assertThat(summary.category()).isEqualTo(FacilityCategory.CAFE);
        assertThat(summary.distanceM()).isEqualTo(1201L);
        assertThat(summary.petAllowed()).isEqualTo(PetAllowed.ALLOWED);
        assertThat(summary.maxWeight()).isEqualByComparingTo("10.00");
        assertThat(summary.requirements()).containsExactly(Requirement.LEASH);
        assertThat(summary.reviewCnt()).isEqualTo(45L);
    }

    @Test
    @DisplayName("리뷰가 없는 시설은 리뷰 수를 0으로 채운다")
    void 리뷰가_없는_시설은_리뷰_수를_0으로_채운다() {
        FacilityRequestDTO.SearchRequest request = createSearchRequest();
        Facility facility = createFacility(2L);

        when(facilityRepository.search(anyDouble(), anyDouble(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(new FacilityWithDistance(facility, 100.0)));
        when(facilityRepository.countSearch(isNull(), isNull(), isNull())).thenReturn(1L);
        when(reviewRepository.countByFacilityIds(List.of(2L))).thenReturn(List.of());

        FacilityResponseDTO.FacilitySearchResult result = facilityQueryService.searchFacilities(request);

        assertThat(result.items().get(0).reviewCnt()).isZero();
    }

    @Test
    @DisplayName("친화도 점수가 없으면 등급 라벨도 내리지 않는다")
    void 친화도_점수가_없으면_등급_라벨도_내리지_않는다() {
        FacilityRequestDTO.SearchRequest request = createSearchRequest();
        Facility facility = createFacility(2L);

        when(facilityRepository.search(anyDouble(), anyDouble(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(new FacilityWithDistance(facility, 100.0)));
        when(facilityRepository.countSearch(isNull(), isNull(), isNull())).thenReturn(1L);
        when(reviewRepository.countByFacilityIds(List.of(2L)))
                .thenReturn(List.of(new FacilityReviewCount(2L, 1000L)));

        FacilityResponseDTO.FacilitySearchResult result = facilityQueryService.searchFacilities(request);

        assertThat(result.items().get(0).petScore()).isNull();
        assertThat(result.items().get(0).rating()).isNull();
    }

    @Test
    @DisplayName("조회 결과가 없으면 리뷰 집계를 조회하지 않는다")
    void 조회_결과가_없으면_리뷰_집계를_조회하지_않는다() {
        FacilityRequestDTO.SearchRequest request = createSearchRequest();

        when(facilityRepository.search(anyDouble(), anyDouble(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of());
        when(facilityRepository.countSearch(isNull(), isNull(), isNull())).thenReturn(0L);

        FacilityResponseDTO.FacilitySearchResult result = facilityQueryService.searchFacilities(request);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        verifyNoInteractions(reviewRepository);
    }

    @Test
    @DisplayName("요청한 페이지 번호와 크기로 조회한다")
    void 요청한_페이지_번호와_크기로_조회한다() {
        FacilityRequestDTO.SearchRequest request = createSearchRequest();
        request.setPage(2);
        request.setSize(30);

        when(facilityRepository.search(anyDouble(), anyDouble(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of());
        when(facilityRepository.countSearch(isNull(), isNull(), isNull())).thenReturn(0L);

        facilityQueryService.searchFacilities(request);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(facilityRepository).search(
                anyDouble(), anyDouble(), isNull(), isNull(), isNull(), pageable.capture());

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(30);
    }

    @Test
    @DisplayName("페이지 크기 기본값은 15다")
    void 페이지_크기_기본값은_15다() {
        FacilityRequestDTO.SearchRequest request = createSearchRequest();

        when(facilityRepository.search(anyDouble(), anyDouble(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of());
        when(facilityRepository.countSearch(isNull(), isNull(), isNull())).thenReturn(0L);

        facilityQueryService.searchFacilities(request);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(facilityRepository).search(
                anyDouble(), anyDouble(), isNull(), isNull(), isNull(), pageable.capture());

        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(15);
    }

    // ------------------------------------------------------------------
    // 시설 상세 조회
    // ------------------------------------------------------------------

    private static final Long FACILITY_ID = 2L;
    private static final Long USER_ID = 7L;

    private Pet createPet(
            Long petId,
            String name,
            Kind kind,
            String weight
    ) {
        Pet pet = Pet.builder()
                .name(name)
                .kind(kind)
                .species("말티즈")
                .weight(new BigDecimal(weight))
                .breedSize(BreedSize.SMALL)
                .build();

        ReflectionTestUtils.setField(pet, "petId", petId);
        return pet;
    }

    private void givenNoPets() {
        when(petRepository.findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(USER_ID))
                .thenReturn(List.of());
    }

    private void givenNoReviews() {
        when(reviewRepository.aggregateByFacilityId(FACILITY_ID, ReviewReportStatus.ACCEPTED))
                .thenReturn(Optional.empty());
    }

    private void givenFacilityAt(double distanceMeter) {
        when(facilityRepository.findWithDistanceById(anyDouble(), anyDouble(), eq(FACILITY_ID)))
                .thenReturn(Optional.of(new FacilityWithDistance(createFacility(FACILITY_ID), distanceMeter)));
    }

    private FacilityResponseDTO.FacilityDetail getDetailFromSeoul() {
        return facilityQueryService.getFacilityDetail(
                FACILITY_ID, USER_ID, SEOUL_LATITUDE, SEOUL_LONGITUDE);
    }

    @Test
    @DisplayName("존재하지 않는 시설이면 FACILITY4041을 던진다")
    void 존재하지_않는_시설이면_FACILITY4041을_던진다() {
        when(facilityRepository.findWithDistanceById(anyDouble(), anyDouble(), eq(FACILITY_ID)))
                .thenReturn(Optional.empty());
        when(facilityRepository.findById(FACILITY_ID)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(GeneralException.class, this::getDetailFromSeoul);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.FACILITY4041);
    }

    @Test
    @DisplayName("좌표를 보내면 거리를 미터 단위 정수로 반올림해 내려준다")
    void 좌표를_보내면_거리를_미터_단위_정수로_반올림해_내려준다() {
        givenFacilityAt(1200.6);
        givenNoReviews();
        givenNoPets();

        FacilityResponseDTO.FacilityDetail result = getDetailFromSeoul();

        assertThat(result.distanceM()).isEqualTo(1201L);
        assertThat(result.facilityId()).isEqualTo(FACILITY_ID);
        assertThat(result.name()).isEqualTo("카페 파도살롱");
    }

    @Test
    @DisplayName("좌표는 소수점 자리를 살리지 않고 내려준다")
    void 좌표는_소수점_자리를_살리지_않고_내려준다() {
        givenFacilityAt(100.0);
        givenNoReviews();
        givenNoPets();

        FacilityResponseDTO.FacilityDetail result = getDetailFromSeoul();

        assertThat(result.latitude()).isEqualTo(37.8);
        assertThat(result.longitude()).isEqualTo(128.9);
    }

    @Test
    @DisplayName("좌표를 보내지 않으면 거리를 비우고 거리 쿼리도 하지 않는다")
    void 좌표를_보내지_않으면_거리를_비우고_거리_쿼리도_하지_않는다() {
        when(facilityRepository.findById(FACILITY_ID))
                .thenReturn(Optional.of(createFacility(FACILITY_ID)));
        givenNoReviews();
        givenNoPets();

        FacilityResponseDTO.FacilityDetail result =
                facilityQueryService.getFacilityDetail(FACILITY_ID, USER_ID, null, null);

        assertThat(result.distanceM()).isNull();
        verify(facilityRepository, never()).findWithDistanceById(anyDouble(), anyDouble(), any());
    }

    @Test
    @DisplayName("좌표가 없는 시설이면 거리만 비우고 나머지는 채워서 내려준다")
    void 좌표가_없는_시설이면_거리만_비우고_나머지는_채워서_내려준다() {
        // 거리 쿼리가 좌표 없는 시설을 걸러내므로 결과가 비어 돌아온다.
        when(facilityRepository.findWithDistanceById(anyDouble(), anyDouble(), eq(FACILITY_ID)))
                .thenReturn(Optional.empty());
        when(facilityRepository.findById(FACILITY_ID))
                .thenReturn(Optional.of(createFacility(FACILITY_ID)));
        givenNoReviews();
        givenNoPets();

        FacilityResponseDTO.FacilityDetail result = getDetailFromSeoul();

        assertThat(result.distanceM()).isNull();
        assertThat(result.name()).isEqualTo("카페 파도살롱");
    }

    @Test
    @DisplayName("최근 1주 내 실시간 거부 제보가 있으면 신뢰도가 UNVERIFIED/DENIAL_REPORT로 내려간다")
    void 최근_거부_제보가_있으면_신뢰도가_내려간다() {
        givenFacilityAt(100.0);
        givenNoReviews();
        givenNoPets();
        when(facilityReportRepository.countByFacility_FacilityIdAndIsRealtimeTrueAndCreatedAtAfter(eq(FACILITY_ID), any()))
                .thenReturn(1L);

        FacilityResponseDTO.FacilityDetail result = getDetailFromSeoul();

        assertThat(result.confidence()).isEqualTo(Confidence.UNVERIFIED);
        assertThat(result.confidenceSource()).isEqualTo(ConfidenceSource.DENIAL_REPORT);
    }

    @Test
    @DisplayName("최근 거부 제보가 없어도 관광공사 원문이 있으면 ESTIMATED/PARSED로 내려준다")
    void 최근_거부_제보가_없으면_원문_유무로_신뢰도를_판단한다() {
        Facility facility = createFacility(FACILITY_ID);
        ReflectionTestUtils.setField(facility, "petConditionRaw", "야외 좌석에 한해 반려동물 동반이 가능합니다.");
        when(facilityRepository.findWithDistanceById(anyDouble(), anyDouble(), eq(FACILITY_ID)))
                .thenReturn(Optional.of(new FacilityWithDistance(facility, 100.0)));
        givenNoReviews();
        givenNoPets();

        FacilityResponseDTO.FacilityDetail result = getDetailFromSeoul();

        assertThat(result.confidence()).isEqualTo(Confidence.ESTIMATED);
        assertThat(result.confidenceSource()).isEqualTo(ConfidenceSource.PARSED);
    }

    @Test
    @DisplayName("원문도 최근 거부 제보도 없으면 UNVERIFIED/NONE으로 내려준다")
    void 아무_신호가_없으면_UNVERIFIED_NONE으로_내려준다() {
        givenFacilityAt(100.0);
        givenNoReviews();
        givenNoPets();

        FacilityResponseDTO.FacilityDetail result = getDetailFromSeoul();

        assertThat(result.confidence()).isEqualTo(Confidence.UNVERIFIED);
        assertThat(result.confidenceSource()).isEqualTo(ConfidenceSource.NONE);
    }

    @Test
    @DisplayName("위도와 경도 중 하나만 보내면 COMMON400을 던진다")
    void 위도와_경도_중_하나만_보내면_COMMON400을_던진다() {
        GeneralException exception = assertThrows(GeneralException.class, () ->
                facilityQueryService.getFacilityDetail(FACILITY_ID, USER_ID, SEOUL_LATITUDE, null));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.COMMON400);
        verifyNoInteractions(facilityRepository, reviewRepository, petRepository);
    }

    @Test
    @DisplayName("리뷰가 없으면 평점은 내리지 않고 등급은 수집 중으로 내려준다")
    void 리뷰가_없으면_평점은_내리지_않고_등급은_수집_중으로_내려준다() {
        givenFacilityAt(100.0);
        givenNoReviews();
        givenNoPets();

        FacilityResponseDTO.FacilityDetail result = getDetailFromSeoul();

        assertThat(result.ratings()).isNull();
        assertThat(result.pawGrade().level()).isZero();
        assertThat(result.pawGrade().label()).isEqualTo("리뷰 수집 중 (0/10)");
    }

    @Test
    @DisplayName("리뷰 집계가 있으면 평점과 등급을 소수 한 자리로 채워서 내려준다")
    void 리뷰_집계가_있으면_평점과_등급을_소수_한_자리로_채워서_내려준다() {
        givenFacilityAt(100.0);
        when(reviewRepository.aggregateByFacilityId(FACILITY_ID, ReviewReportStatus.ACCEPTED))
                .thenReturn(Optional.of(new FacilityReviewAggregate(
                        FACILITY_ID, 90L, 88.44, 4.53, 4.78, 4.21)));
        givenNoPets();

        FacilityResponseDTO.FacilityDetail result = getDetailFromSeoul();

        assertThat(result.ratings().score()).isEqualTo(88.4);
        assertThat(result.ratings().spaceRating()).isEqualTo(4.5);
        assertThat(result.ratings().customerService()).isEqualTo(4.8);
        assertThat(result.ratings().amenitiesRating()).isEqualTo(4.2);
        assertThat(result.pawGrade().level()).isEqualTo(4);
        assertThat(result.pawGrade().label()).isEqualTo("동반 우수");
    }

    @Test
    @DisplayName("등급 판정은 표시용으로 반올림하기 전 점수로 한다")
    void 등급_판정은_표시용으로_반올림하기_전_점수로_한다() {
        // 87.96은 표시용으로 88.0이 되지만 88점을 넘긴 적이 없으므로 한 단계 아래 등급이어야 한다.
        givenFacilityAt(100.0);
        when(reviewRepository.aggregateByFacilityId(FACILITY_ID, ReviewReportStatus.ACCEPTED))
                .thenReturn(Optional.of(new FacilityReviewAggregate(
                        FACILITY_ID, 90L, 87.96, 4.4, 4.4, 4.4)));
        givenNoPets();

        FacilityResponseDTO.FacilityDetail result = getDetailFromSeoul();

        assertThat(result.ratings().score()).isEqualTo(88.0);
        assertThat(result.pawGrade().level()).isEqualTo(3);
        assertThat(result.pawGrade().label()).isEqualTo("동반 추천");
    }

    @Test
    @DisplayName("사용자의 반려동물을 조회 순서 그대로 내려준다")
    void 사용자의_반려동물을_조회_순서_그대로_내려준다() {
        givenFacilityAt(100.0);
        givenNoReviews();
        when(petRepository.findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(USER_ID))
                .thenReturn(List.of(
                        createPet(1L, "몽이", Kind.DOG, "3.20"),
                        createPet(2L, "나비", Kind.CAT, "4.10")
                ));

        FacilityResponseDTO.FacilityDetail result = getDetailFromSeoul();

        assertThat(result.pets()).hasSize(2);
        assertThat(result.pets().get(0).petId()).isEqualTo(1L);
        assertThat(result.pets().get(0).name()).isEqualTo("몽이");
        assertThat(result.pets().get(0).weight()).isEqualByComparingTo("3.20");
        assertThat(result.pets().get(1).name()).isEqualTo("나비");
    }

    @Test
    @DisplayName("개와 고양이만 있으면 hasNonDogCatPet은 false다")
    void 개와_고양이만_있으면_hasNonDogCatPet은_false다() {
        givenFacilityAt(100.0);
        givenNoReviews();
        when(petRepository.findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(USER_ID))
                .thenReturn(List.of(
                        createPet(1L, "몽이", Kind.DOG, "3.20"),
                        createPet(2L, "나비", Kind.CAT, "4.10")
                ));

        FacilityResponseDTO.FacilityDetail result = getDetailFromSeoul();

        assertThat(result.hasNonDogCatPet()).isFalse();
    }

    @Test
    @DisplayName("개와 고양이가 아닌 반려동물이 있으면 hasNonDogCatPet은 true다")
    void 개와_고양이가_아닌_반려동물이_있으면_hasNonDogCatPet은_true다() {
        givenFacilityAt(100.0);
        givenNoReviews();
        when(petRepository.findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(USER_ID))
                .thenReturn(List.of(
                        createPet(1L, "몽이", Kind.DOG, "3.20"),
                        createPet(3L, "초록이", Kind.PARROT, "0.40")
                ));

        FacilityResponseDTO.FacilityDetail result = getDetailFromSeoul();

        assertThat(result.hasNonDogCatPet()).isTrue();
    }

    @Test
    @DisplayName("동반 조건 안내문은 전용 컬럼 값을 그대로 내려준다")
    void 동반_조건_안내문은_전용_컬럼_값을_그대로_내려준다() {
        Facility facility = createFacility(FACILITY_ID);
        ReflectionTestUtils.setField(facility, "petConditionRaw", "10kg 이하 소형견에 한해 실내 입장이 가능합니다.");

        // 관광공사 원문은 표현이 제각각이라 안내문으로 쓰지 않는다. 값이 달라도 섞이면 안 된다.
        ReflectionTestUtils.setField(facility, "allowedAnimalText", "전 견종 동반 가능");
        ReflectionTestUtils.setField(facility, "confirmedAt", LocalDateTime.of(2026, 7, 5, 0, 0));

        when(facilityRepository.findWithDistanceById(anyDouble(), anyDouble(), eq(FACILITY_ID)))
                .thenReturn(Optional.of(new FacilityWithDistance(facility, 100.0)));
        givenNoReviews();
        givenNoPets();

        FacilityResponseDTO.FacilityDetail result = getDetailFromSeoul();

        assertThat(result.petConditionRaw()).isEqualTo("10kg 이하 소형견에 한해 실내 입장이 가능합니다.");
        assertThat(result.confirmedAt()).isEqualTo(LocalDateTime.of(2026, 7, 5, 0, 0));
    }

    @Test
    @DisplayName("안내문을 아직 채우지 않은 시설은 동반 조건을 비워서 내려준다")
    void 안내문을_아직_채우지_않은_시설은_동반_조건을_비워서_내려준다() {
        Facility facility = createFacility(FACILITY_ID);

        // 관광공사 원문만 있는 상태다. 이걸 이어 붙여 안내문을 만들어내지 않는다.
        ReflectionTestUtils.setField(facility, "allowedAnimalText", "전 견종 동반 가능");

        when(facilityRepository.findWithDistanceById(anyDouble(), anyDouble(), eq(FACILITY_ID)))
                .thenReturn(Optional.of(new FacilityWithDistance(facility, 100.0)));
        givenNoReviews();
        givenNoPets();

        assertThat(getDetailFromSeoul().petConditionRaw()).isNull();
    }

    // ------------------------------------------------------------------
    // 발자국 랭킹
    // ------------------------------------------------------------------

    private FacilityRequestDTO.RankingRequest createRankingRequest() {
        return new FacilityRequestDTO.RankingRequest();
    }

    private Facility createGradedFacility(
            Long facilityId,
            double petScore,
            long reviewCount
    ) {
        Facility facility = createFacility(facilityId);
        facility.applyReviewAggregate(new FacilityReviewAggregate(
                facilityId, reviewCount, petScore, 4.5, 4.8, 4.2
        ));

        return facility;
    }

    @Test
    @DisplayName("좌표가 없으면 거리를 계산하지 않고 조회한다")
    void 좌표가_없으면_거리를_계산하지_않고_조회한다() {
        when(facilityRepository.searchRanking(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(createGradedFacility(1L, 96.2, 160)));
        when(facilityRepository.countRanking(isNull(), isNull(), isNull(), isNull(), eq(false)))
                .thenReturn(1L);

        FacilityResponseDTO.RankingResult result = facilityQueryService.getRanking(createRankingRequest());

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).distanceM()).isNull();
        verify(facilityRepository, never())
                .searchRankingWithDistance(anyDouble(), anyDouble(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("좌표가 있으면 거리를 함께 계산한다")
    void 좌표가_있으면_거리를_함께_계산한다() {
        FacilityRequestDTO.RankingRequest request = createRankingRequest();
        request.setLatitude(SEOUL_LATITUDE);
        request.setLongitude(SEOUL_LONGITUDE);

        when(facilityRepository.searchRankingWithDistance(
                anyDouble(), anyDouble(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(new FacilityWithDistance(createGradedFacility(1L, 96.2, 160), 1900.4)));
        when(facilityRepository.countRanking(isNull(), isNull(), isNull(), isNull(), eq(true)))
                .thenReturn(1L);

        FacilityResponseDTO.RankingResult result = facilityQueryService.getRanking(request);

        assertThat(result.items().get(0).distanceM()).isEqualTo(1900L);
    }

    @Test
    @DisplayName("반경이 있으면 경계 사각형과 반경을 함께 넘긴다")
    void 랭킹은_반경이_있으면_경계_사각형과_반경을_함께_넘긴다() {
        FacilityRequestDTO.RankingRequest request = createRankingRequest();
        request.setLatitude(SEOUL_LATITUDE);
        request.setLongitude(SEOUL_LONGITUDE);
        request.setRadiusM(3000);

        when(facilityRepository.searchRankingWithinRadius(
                anyDouble(), anyDouble(), any(), any(), any(), any(),
                any(), any(), any(), any(), anyDouble(), any(Pageable.class)))
                .thenReturn(List.of());
        when(facilityRepository.countRankingWithinRadius(
                anyDouble(), anyDouble(), any(), any(), any(), any(),
                any(), any(), any(), any(), anyDouble()))
                .thenReturn(0L);

        facilityQueryService.getRanking(request);

        verify(facilityRepository).searchRankingWithinRadius(
                anyDouble(), anyDouble(), any(), any(), any(), any(),
                any(), any(), any(), any(), eq(3000.0), any(Pageable.class));
    }

    @Test
    @DisplayName("순위는 페이지를 넘겨도 이어진다")
    void 순위는_페이지를_넘겨도_이어진다() {
        FacilityRequestDTO.RankingRequest request = createRankingRequest();
        request.setPage(1);
        request.setSize(20);

        when(facilityRepository.searchRanking(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(
                        createGradedFacility(1L, 96.2, 160),
                        createGradedFacility(2L, 88.4, 96)
                ));
        when(facilityRepository.countRanking(isNull(), isNull(), isNull(), isNull(), eq(false)))
                .thenReturn(42L);

        FacilityResponseDTO.RankingResult result = facilityQueryService.getRanking(request);

        assertThat(result.items()).extracting(FacilityResponseDTO.RankingItem::rank)
                .containsExactly(21, 22);
        assertThat(result.total()).isEqualTo(42L);
    }

    @Test
    @DisplayName("등급과 점수를 저장된 값 그대로 내려준다")
    void 등급과_점수를_저장된_값_그대로_내려준다() {
        when(facilityRepository.searchRanking(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(createGradedFacility(1L, 96.24, 160)));
        when(facilityRepository.countRanking(isNull(), isNull(), isNull(), isNull(), eq(false)))
                .thenReturn(1L);

        FacilityResponseDTO.RankingItem item = facilityQueryService
                .getRanking(createRankingRequest())
                .items()
                .get(0);

        assertThat(item.pawGrade().level()).isEqualTo(5);
        assertThat(item.pawGrade().label()).isEqualTo("최고 등급");
        assertThat(item.petScore()).isEqualTo(96.2);
        assertThat(item.reviewCnt()).isEqualTo(160L);
    }

    @Test
    @DisplayName("위도만 보내면 400으로 막는다")
    void 랭킹에서_위도만_보내면_400으로_막는다() {
        FacilityRequestDTO.RankingRequest request = createRankingRequest();
        request.setLatitude(SEOUL_LATITUDE);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> facilityQueryService.getRanking(request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.COMMON400);
        verifyNoInteractions(facilityRepository);
    }

    @Test
    @DisplayName("좌표 없이 거리 필터만 보내면 400으로 막는다")
    void 좌표_없이_거리_필터만_보내면_400으로_막는다() {
        FacilityRequestDTO.RankingRequest request = createRankingRequest();
        request.setRadiusM(3000);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> facilityQueryService.getRanking(request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.COMMON400);
        verifyNoInteractions(facilityRepository);
    }

    @Test
    @DisplayName("시도 코드 없이 시군구 코드만 보내면 400으로 막는다")
    void 시도_코드_없이_시군구_코드만_보내면_400으로_막는다() {
        FacilityRequestDTO.RankingRequest request = createRankingRequest();
        request.setSigunguCode("1");

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> facilityQueryService.getRanking(request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.COMMON400);
        verifyNoInteractions(facilityRepository);
    }

    // ------------------------------------------------------------------
    // 지역 목록
    // ------------------------------------------------------------------

    private Region createRegion(
            String sidoCode,
            String sido,
            String sigunguCode,
            String sigungu
    ) {
        return Region.builder()
                .sidoCode(sidoCode)
                .sido(sido)
                .sigunguCode(sigunguCode)
                .sigungu(sigungu)
                .build();
    }

    private void givenRegions(List<Region> regions) {
        when(regionRepository.findAllByOrderBySidoCodeAscSigunguCodeAsc()).thenReturn(regions);
    }

    @Test
    @DisplayName("시도별로 시군구를 묶어 내려준다")
    void 시도별로_시군구를_묶어_내려준다() {
        givenRegions(List.of(
                createRegion("11", "서울특별시", "680", "강남구"),
                createRegion("32", "강원특별자치도", "010", "강릉시"),
                createRegion("32", "강원특별자치도", "070", "속초시")
        ));

        List<FacilityResponseDTO.Region> result = facilityQueryService.getRegions();

        assertThat(result).hasSize(2);
        assertThat(result.get(1).sido()).isEqualTo("강원특별자치도");
        assertThat(result.get(1).sigungus())
                .extracting(FacilityResponseDTO.Sigungu::sigungu)
                .containsExactly("강릉시", "속초시");
    }

    @Test
    @DisplayName("지역 테이블이 준 코드 순서를 그대로 유지한다")
    void 지역_테이블이_준_코드_순서를_그대로_유지한다() {
        // 시설이 몇 곳인지로 재정렬하지 않는다. 칩 위치가 리뷰에 따라 흔들리면
        // 사용자가 자기 지역이 어디쯤 있는지 기억할 수 없다.
        givenRegions(List.of(
                createRegion("11", "서울특별시", "680", "강남구"),
                createRegion("26", "부산광역시", "710", "해운대구"),
                createRegion("32", "강원특별자치도", "010", "강릉시")
        ));

        assertThat(facilityQueryService.getRegions())
                .extracting(FacilityResponseDTO.Region::sidoCode)
                .containsExactly("11", "26", "32");
    }

    @Test
    @DisplayName("시군구가 없는 시도는 하위 칩 없이 내려준다")
    void 시군구가_없는_시도는_하위_칩_없이_내려준다() {
        givenRegions(List.of(createRegion("36", "세종특별자치시", null, null)));

        List<FacilityResponseDTO.Region> result = facilityQueryService.getRegions();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sido()).isEqualTo("세종특별자치시");
        assertThat(result.get(0).sigungus()).isEmpty();
    }

    @Test
    @DisplayName("지역 테이블이 비어 있으면 빈 목록을 내려준다")
    void 지역_테이블이_비어_있으면_빈_목록을_내려준다() {
        givenRegions(List.of());

        assertThat(facilityQueryService.getRegions()).isEmpty();
    }
}
