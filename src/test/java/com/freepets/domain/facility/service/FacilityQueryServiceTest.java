package com.freepets.domain.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

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
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.repository.FacilityWithDistance;
import com.freepets.domain.review.repository.FacilityReviewCount;
import com.freepets.domain.review.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class FacilityQueryServiceTest {

    private static final double SEOUL_LATITUDE = 37.5665;
    private static final double SEOUL_LONGITUDE = 126.9780;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private ReviewRepository reviewRepository;

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
}
