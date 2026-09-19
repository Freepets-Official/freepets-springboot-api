package com.freepets.domain.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import com.freepets.domain.facility.entity.Region;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.repository.RegionRepository;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.infra.tourapi.FacilityCategoryMapper;
import com.freepets.infra.tourapi.TourApiClient;
import com.freepets.infra.tourapi.TourApiException;
import com.freepets.infra.tourapi.TourApiResponseParser;

@ExtendWith(MockitoExtension.class)
class FacilityListQueryServiceTest {

    private static final String SIDO_CODE_GYEONGGI = "41";
    private static final String SIGUNGU_CODE_PAJU = "480";

    /** 하위 시군구 행이 없는 시도. 시군구를 비워도 조회되어야 한다. */
    private static final String SIDO_CODE_WITHOUT_SIGUNGU = "99";

    @Mock
    private TourApiClient tourApiClient;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private RegionRepository regionRepository;

    // 파서와 분류 매퍼는 의존이 없는 순수 변환이라 진짜를 쓴다. 관광공사 응답 JSON을 실제로
    // 훑고 contentTypeId 매핑까지 함께 검증된다.
    private final TourApiResponseParser tourApiResponseParser = new TourApiResponseParser();
    private final FacilityCategoryMapper facilityCategoryMapper = new FacilityCategoryMapper();

    private FacilityListQueryService facilityListQueryService;

    @BeforeEach
    void setUp() {
        facilityListQueryService = new FacilityListQueryService(
                tourApiClient,
                tourApiResponseParser,
                facilityCategoryMapper,
                facilityRepository,
                reviewRepository,
                regionRepository
        );
    }

    // ------------------------------------------------------------------
    // 지역 검증
    // ------------------------------------------------------------------

    @Test
    @DisplayName("시군구를 비우면 400이다")
    void 시군구를_비우면_400이다() {
        FacilityRequestDTO.FacilityListRequest request = createRequest(SIDO_CODE_GYEONGGI, null);

        when(regionRepository.existsBySidoCodeAndSigunguCodeIsNotNull(SIDO_CODE_GYEONGGI))
                .thenReturn(true);

        GeneralException exception = assertThrows(GeneralException.class,
                () -> facilityListQueryService.getFacilityList(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.COMMON400);
        verifyNoInteractions(tourApiClient);
    }

    @Test
    @DisplayName("하위 시군구 행이 없는 시도는 시군구 없이 조회된다")
    void 하위_시군구_행이_없는_시도는_시군구_없이_조회된다() {
        FacilityRequestDTO.FacilityListRequest request = createRequest(SIDO_CODE_WITHOUT_SIGUNGU, null);

        when(regionRepository.existsBySidoCodeAndSigunguCodeIsNotNull(SIDO_CODE_WITHOUT_SIGUNGU))
                .thenReturn(false);
        when(regionRepository.findBySidoCodeAndSigunguCode(SIDO_CODE_WITHOUT_SIGUNGU, null))
                .thenReturn(Optional.of(region()));
        when(tourApiClient.areaBasedList(isNull(), eq(SIDO_CODE_WITHOUT_SIGUNGU), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(emptyResponse());

        FacilityResponseDTO.FacilityListResult result = facilityListQueryService.getFacilityList(request);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    @DisplayName("없는 지역 코드는 400이다")
    void 없는_지역_코드는_400이다() {
        FacilityRequestDTO.FacilityListRequest request = createRequest(SIDO_CODE_GYEONGGI, "999");

        when(regionRepository.findBySidoCodeAndSigunguCode(SIDO_CODE_GYEONGGI, "999"))
                .thenReturn(Optional.empty());

        GeneralException exception = assertThrows(GeneralException.class,
                () -> facilityListQueryService.getFacilityList(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.COMMON400);
        verifyNoInteractions(tourApiClient);
    }

    // ------------------------------------------------------------------
    // 관광공사 응답을 우리 시설로 옮기기
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DB에 없는 contentId는 목록에서 빠진다")
    void DB에_없는_contentId는_목록에서_빠진다() {
        givenValidRegion();
        givenTourApiItems("""
                {"contentid":"100","lclsSystm2":"FD01"},
                {"contentid":"200","lclsSystm2":"FD01"}
                """, 2);

        // 200번은 아직 적재되지 않아 조회되지 않는다.
        when(facilityRepository.findByContentIdIn(List.of("100", "200")))
                .thenReturn(List.of(facility(1L, "100", "가게", PetAllowed.ALLOWED)));
        when(reviewRepository.countByFacilityIds(List.of(1L))).thenReturn(List.of());

        FacilityResponseDTO.FacilityListResult result =
                facilityListQueryService.getFacilityList(createRequest(SIDO_CODE_GYEONGGI, SIGUNGU_CODE_PAJU));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).facilityId()).isEqualTo(1L);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("동반 가능 필터를 걸면 그 시설만 남고 총 건수도 필터 뒤 값이다")
    void 동반_가능_필터를_걸면_그_시설만_남는다() {
        givenValidRegion();
        givenTourApiItems("""
                {"contentid":"100","lclsSystm2":"FD01"},
                {"contentid":"200","lclsSystm2":"FD01"},
                {"contentid":"300","lclsSystm2":"FD01"}
                """, 3);

        when(facilityRepository.findByContentIdIn(any())).thenReturn(List.of(
                facility(1L, "100", "가게", PetAllowed.ALLOWED),
                facility(2L, "200", "나게", PetAllowed.PENDING),
                facility(3L, "300", "다게", PetAllowed.DENIED)
        ));
        when(reviewRepository.countByFacilityIds(List.of(1L))).thenReturn(List.of());

        FacilityRequestDTO.FacilityListRequest request = createRequest(SIDO_CODE_GYEONGGI, SIGUNGU_CODE_PAJU);
        request.setPetAllowed(PetAllowed.ALLOWED);

        FacilityResponseDTO.FacilityListResult result = facilityListQueryService.getFacilityList(request);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).petAllowed()).isEqualTo(PetAllowed.ALLOWED);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("이름 가나다순으로 정렬한다")
    void 이름_가나다순으로_정렬한다() {
        givenValidRegion();
        givenTourApiItems("""
                {"contentid":"100","lclsSystm2":"FD01"},
                {"contentid":"200","lclsSystm2":"FD01"},
                {"contentid":"300","lclsSystm2":"FD01"}
                """, 3);

        when(facilityRepository.findByContentIdIn(any())).thenReturn(List.of(
                facility(1L, "100", "하늘카페", PetAllowed.ALLOWED),
                facility(2L, "200", "가람카페", PetAllowed.ALLOWED),
                facility(3L, "300", "나루카페", PetAllowed.ALLOWED)
        ));
        when(reviewRepository.countByFacilityIds(any())).thenReturn(List.of());

        FacilityResponseDTO.FacilityListResult result =
                facilityListQueryService.getFacilityList(createRequest(SIDO_CODE_GYEONGGI, SIGUNGU_CODE_PAJU));

        assertThat(result.items())
                .extracting(FacilityResponseDTO.FacilityListItem::name)
                .containsExactly("가람카페", "나루카페", "하늘카페");
    }

    @Test
    @DisplayName("사업자가 직접 등록한 시설도 함께 내려가며 이름순에 섞인다")
    void 사업자가_직접_등록한_시설도_함께_내려간다() {
        givenValidRegion();
        givenTourApiItems("""
                {"contentid":"100","lclsSystm2":"FD01"}
                """, 1);

        when(facilityRepository.findByContentIdIn(List.of("100")))
                .thenReturn(List.of(facility(1L, "100", "하늘카페", PetAllowed.ALLOWED)));
        // 관광공사에 없는 시설이라 contentId로는 영영 찾히지 않는다.
        when(facilityRepository.findAllWithoutContentId(isNull(), isNull(),
                eq(SIDO_CODE_GYEONGGI), eq(SIGUNGU_CODE_PAJU)))
                .thenReturn(List.of(selfRegisteredFacility(2L, "가람카페")));
        when(reviewRepository.countByFacilityIds(any())).thenReturn(List.of());

        FacilityResponseDTO.FacilityListResult result =
                facilityListQueryService.getFacilityList(createRequest(SIDO_CODE_GYEONGGI, SIGUNGU_CODE_PAJU));

        assertThat(result.items())
                .extracting(FacilityResponseDTO.FacilityListItem::name)
                .containsExactly("가람카페", "하늘카페");
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("페이지를 넘기면 그만큼 건너뛰고, 총 건수는 전체를 센다")
    void 페이지를_넘기면_그만큼_건너뛴다() {
        givenValidRegion();
        givenTourApiItems("""
                {"contentid":"100","lclsSystm2":"FD01"},
                {"contentid":"200","lclsSystm2":"FD01"},
                {"contentid":"300","lclsSystm2":"FD01"}
                """, 3);

        when(facilityRepository.findByContentIdIn(any())).thenReturn(List.of(
                facility(1L, "100", "가게", PetAllowed.ALLOWED),
                facility(2L, "200", "나게", PetAllowed.ALLOWED),
                facility(3L, "300", "다게", PetAllowed.ALLOWED)
        ));
        when(reviewRepository.countByFacilityIds(List.of(3L))).thenReturn(List.of());

        FacilityRequestDTO.FacilityListRequest request = createRequest(SIDO_CODE_GYEONGGI, SIGUNGU_CODE_PAJU);
        request.setPage(1);
        request.setSize(2);

        FacilityResponseDTO.FacilityListResult result = facilityListQueryService.getFacilityList(request);

        assertThat(result.items()).extracting(FacilityResponseDTO.FacilityListItem::name)
                .containsExactly("다게");
        assertThat(result.total()).isEqualTo(3);
    }

    // ------------------------------------------------------------------
    // 분류 매핑
    // ------------------------------------------------------------------

    @Test
    @DisplayName("카페는 분류체계 중분류까지 실어 호출한다")
    void 카페는_분류체계_중분류까지_실어_호출한다() {
        givenValidRegion();
        when(tourApiClient.areaBasedList(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(emptyResponse());

        FacilityRequestDTO.FacilityListRequest request = createRequest(SIDO_CODE_GYEONGGI, SIGUNGU_CODE_PAJU);
        request.setCategory(FacilityCategory.CAFE);

        facilityListQueryService.getFacilityList(request);

        ArgumentCaptor<Integer> contentTypeId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> mediumCategoryCode = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(tourApiClient).areaBasedList(
                contentTypeId.capture(),
                eq(SIDO_CODE_GYEONGGI),
                eq(SIGUNGU_CODE_PAJU),
                mediumCategoryCode.capture(),
                anyInt(),
                anyInt()
        );

        assertThat(contentTypeId.getValue()).isEqualTo(39);
        assertThat(mediumCategoryCode.getValue()).isEqualTo("FD05");
    }

    @Test
    @DisplayName("음식점은 중분류 없이 받아 카페를 서버에서 걷어낸다")
    void 음식점은_카페를_서버에서_걷어낸다() {
        givenValidRegion();
        givenTourApiItems("""
                {"contentid":"100","lclsSystm2":"FD01"},
                {"contentid":"200","lclsSystm2":"FD05"}
                """, 2);

        // 카페(FD05)인 200번은 DB 조회 대상에서부터 빠져야 한다.
        when(facilityRepository.findByContentIdIn(List.of("100")))
                .thenReturn(List.of(facility(1L, "100", "국밥집", PetAllowed.ALLOWED)));
        when(reviewRepository.countByFacilityIds(List.of(1L))).thenReturn(List.of());

        FacilityRequestDTO.FacilityListRequest request = createRequest(SIDO_CODE_GYEONGGI, SIGUNGU_CODE_PAJU);
        request.setCategory(FacilityCategory.RESTAURANT);

        FacilityResponseDTO.FacilityListResult result = facilityListQueryService.getFacilityList(request);

        assertThat(result.items()).extracting(FacilityResponseDTO.FacilityListItem::name)
                .containsExactly("국밥집");
    }

    // ------------------------------------------------------------------
    // 폴백
    // ------------------------------------------------------------------

    @Test
    @DisplayName("관광공사 호출이 실패하면 DB로 대신 응답한다")
    void 관광공사_호출이_실패하면_DB로_대신_응답한다() {
        givenValidRegion();
        when(tourApiClient.areaBasedList(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new TourApiException("일일 한도를 넘었습니다."));

        when(facilityRepository.searchAll(isNull(), isNull(), eq(SIDO_CODE_GYEONGGI), eq(SIGUNGU_CODE_PAJU),
                any(Pageable.class)))
                .thenReturn(List.of(facility(1L, "100", "가게", PetAllowed.ALLOWED)));
        when(facilityRepository.countAll(isNull(), isNull(), eq(SIDO_CODE_GYEONGGI), eq(SIGUNGU_CODE_PAJU)))
                .thenReturn(7L);
        when(reviewRepository.countByFacilityIds(List.of(1L))).thenReturn(List.of());

        FacilityResponseDTO.FacilityListResult result =
                facilityListQueryService.getFacilityList(createRequest(SIDO_CODE_GYEONGGI, SIGUNGU_CODE_PAJU));

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(7);
    }

    // ------------------------------------------------------------------
    // 고정 데이터
    // ------------------------------------------------------------------

    private void givenValidRegion() {
        when(regionRepository.findBySidoCodeAndSigunguCode(SIDO_CODE_GYEONGGI, SIGUNGU_CODE_PAJU))
                .thenReturn(Optional.of(region()));
    }

    private void givenTourApiItems(
            String itemsJson,
            int totalCount
    ) {
        when(tourApiClient.areaBasedList(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(response(itemsJson, totalCount));
    }

    private String response(
            String itemsJson,
            int totalCount
    ) {
        return """
                {"response":{"header":{"resultCode":"0000"},"body":{
                  "items":{"item":[%s]},"totalCount":%d}}}
                """.formatted(itemsJson, totalCount);
    }

    private String emptyResponse() {
        return """
                {"response":{"header":{"resultCode":"0000"},"body":{"items":"","totalCount":0}}}
                """;
    }

    private FacilityRequestDTO.FacilityListRequest createRequest(
            String sidoCode,
            String sigunguCode
    ) {
        FacilityRequestDTO.FacilityListRequest request = new FacilityRequestDTO.FacilityListRequest();
        request.setSidoCode(sidoCode);
        request.setSigunguCode(sigunguCode);
        return request;
    }

    private Region region() {
        return Region.builder()
                .sidoCode(SIDO_CODE_GYEONGGI)
                .sido("경기도")
                .sigunguCode(SIGUNGU_CODE_PAJU)
                .sigungu("파주시")
                .build();
    }

    /** 사업자가 직접 등록한 시설. 관광공사 콘텐츠 ID가 없다. */
    private Facility selfRegisteredFacility(
            Long facilityId,
            String name
    ) {
        Facility facility = Facility.builder()
                .name(name)
                .category(FacilityCategory.CAFE)
                .address("경기 파주시 회동길 2")
                .sidoCode(SIDO_CODE_GYEONGGI)
                .sigunguCode(SIGUNGU_CODE_PAJU)
                .sido("경기도")
                .sigungu("파주시")
                .petAllowed(PetAllowed.ALLOWED)
                .source(FacilitySource.BUSINESS_SELF)
                .isActive(true)
                .build();

        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

    private Facility facility(
            Long facilityId,
            String contentId,
            String name,
            PetAllowed petAllowed
    ) {
        Facility facility = Facility.builder()
                .contentId(contentId)
                .name(name)
                .category(FacilityCategory.CAFE)
                .address("경기 파주시 회동길 1")
                .sidoCode(SIDO_CODE_GYEONGGI)
                .sigunguCode(SIGUNGU_CODE_PAJU)
                .sido("경기도")
                .sigungu("파주시")
                .petAllowed(petAllowed)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();

        // facilityId는 DB가 채우는 값이라 빌더에 없다.
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

}
