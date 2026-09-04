package com.freepets.domain.facility.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freepets.domain.facility.dto.FacilityResponseDTO;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.facility.service.FacilityQueryService;

@WebMvcTest(FacilityController.class)
@AutoConfigureMockMvc(addFilters = false)
class FacilityControllerTest {

    private static final String SEARCH_PATH = "/api/v1/facilities/search";
    private static final String RANKING_PATH = "/api/v1/facilities/ranking";
    private static final String REGIONS_PATH = "/api/v1/facilities/regions";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacilityQueryService facilityQueryService;

    private FacilityResponseDTO.FacilitySearchResult createSearchResult() {
        FacilityResponseDTO.FacilitySummary summary = new FacilityResponseDTO.FacilitySummary(
                2L,
                "카페 파도살롱",
                FacilityCategory.CAFE,
                "강원 강릉시 창해로 17",
                1200L,
                PetAllowed.PENDING,
                new BigDecimal("10.00"),
                List.of(Requirement.LEASH),
                null,
                null,
                45L
        );

        return new FacilityResponseDTO.FacilitySearchResult(List.of(summary), 14L);
    }

    @Test
    @DisplayName("검색에 성공하면 200과 시설 목록을 반환한다")
    void 검색에_성공하면_200과_시설_목록을_반환한다() throws Exception {
        when(facilityQueryService.searchFacilities(any())).thenReturn(createSearchResult());

        mockMvc.perform(post(SEARCH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5665,\"longitude\":126.9780}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.total").value(14))
                .andExpect(jsonPath("$.result.items[0].facilityId").value(2))
                .andExpect(jsonPath("$.result.items[0].name").value("카페 파도살롱"))
                .andExpect(jsonPath("$.result.items[0].category").value("CAFE"))
                .andExpect(jsonPath("$.result.items[0].distanceM").value(1200))
                .andExpect(jsonPath("$.result.items[0].petAllowed").value("PENDING"))
                .andExpect(jsonPath("$.result.items[0].requirements[0]").value("LEASH"))
                .andExpect(jsonPath("$.result.items[0].reviewCnt").value(45));
    }

    @Test
    @DisplayName("위도와 경도가 없으면 400을 반환한다")
    void 위도와_경도가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post(SEARCH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.latitude").exists())
                .andExpect(jsonPath("$.result.longitude").exists());

        verifyNoInteractions(facilityQueryService);
    }

    @Test
    @DisplayName("위도가 범위를 벗어나면 400을 반환한다")
    void 위도가_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(post(SEARCH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":91.0,\"longitude\":126.9780}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.latitude").exists());

        verifyNoInteractions(facilityQueryService);
    }

    @Test
    @DisplayName("category가 정의된 값이 아니면 400과 허용값 안내를 반환한다")
    void category가_정의된_값이_아니면_400과_허용값_안내를_반환한다() throws Exception {
        mockMvc.perform(post(SEARCH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5665,\"longitude\":126.9780,\"category\":\"HOTEL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.category").value(
                        "category는 TOUR, CULTURE, FESTIVAL, LEISURE, STAY, SHOPPING, RESTAURANT, CAFE 중 하나여야 합니다."));

        verifyNoInteractions(facilityQueryService);
    }

    @Test
    @DisplayName("petAllowed가 정의된 값이 아니면 400과 허용값 안내를 반환한다")
    void petAllowed가_정의된_값이_아니면_400과_허용값_안내를_반환한다() throws Exception {
        mockMvc.perform(post(SEARCH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5665,\"longitude\":126.9780,\"petAllowed\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.petAllowed").value(
                        "petAllowed는 ALLOWED, DENIED, PENDING 중 하나여야 합니다."));

        verifyNoInteractions(facilityQueryService);
    }

    @Test
    @DisplayName("검색 반경이 허용 범위를 벗어나면 400을 반환한다")
    void 검색_반경이_허용_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(post(SEARCH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5665,\"longitude\":126.9780,\"radiusM\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.radiusM").exists());

        verifyNoInteractions(facilityQueryService);
    }

    @Test
    @DisplayName("페이지 크기가 상한을 넘으면 400을 반환한다")
    void 페이지_크기가_상한을_넘으면_400을_반환한다() throws Exception {
        mockMvc.perform(post(SEARCH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5665,\"longitude\":126.9780,\"size\":101}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.size").exists());

        verifyNoInteractions(facilityQueryService);
    }

    // ------------------------------------------------------------------
    // 시설 상세 조회
    // ------------------------------------------------------------------

    private static final String DETAIL_PATH = "/api/v1/facilities/{facilityId}";

    private FacilityResponseDTO.FacilityDetail createDetail() {
        return new FacilityResponseDTO.FacilityDetail(
                2L,
                "카페 파도살롱",
                FacilityCategory.CAFE,
                "강원 강릉시 창해로 17",
                "033-651-2287",
                37.8016,
                128.9107,
                1200L,
                PetAllowed.PENDING,
                null,
                LocalDateTime.of(2026, 7, 5, 0, 0),
                "https://tong.visitkorea.or.kr/image.jpg",
                "https://tong.visitkorea.or.kr/thumbnail.jpg",
                new FacilityResponseDTO.PawGrade(4, "동반 우수"),
                new FacilityResponseDTO.Ratings(88.4, 4.5, 4.8, 4.2),
                List.of(new FacilityResponseDTO.OwnedPet(1L, "몽이", new BigDecimal("3.20"))),
                false
        );
    }

    @Test
    @DisplayName("상세 조회에 성공하면 200과 시설 상세를 반환한다")
    void 상세_조회에_성공하면_200과_시설_상세를_반환한다() throws Exception {
        when(facilityQueryService.getFacilityDetail(eq(2L), any(), any(), any()))
                .thenReturn(createDetail());

        mockMvc.perform(get(DETAIL_PATH, 2L)
                        .param("latitude", "37.8016")
                        .param("longitude", "128.9107"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.facilityId").value(2))
                .andExpect(jsonPath("$.result.name").value("카페 파도살롱"))
                .andExpect(jsonPath("$.result.category").value("CAFE"))
                .andExpect(jsonPath("$.result.phone").value("033-651-2287"))
                .andExpect(jsonPath("$.result.latitude").value(37.8016))
                .andExpect(jsonPath("$.result.longitude").value(128.9107))
                .andExpect(jsonPath("$.result.distanceM").value(1200))
                .andExpect(jsonPath("$.result.petAllowed").value("PENDING"))
                .andExpect(jsonPath("$.result.imageUrl").value("https://tong.visitkorea.or.kr/image.jpg"))
                .andExpect(jsonPath("$.result.pawGrade.level").value(4))
                .andExpect(jsonPath("$.result.pawGrade.label").value("동반 우수"))
                .andExpect(jsonPath("$.result.ratings.score").value(88.4))
                .andExpect(jsonPath("$.result.ratings.spaceRating").value(4.5))
                .andExpect(jsonPath("$.result.ratings.customerService").value(4.8))
                .andExpect(jsonPath("$.result.ratings.amenitiesRating").value(4.2))
                .andExpect(jsonPath("$.result.pets[0].petId").value(1))
                .andExpect(jsonPath("$.result.pets[0].name").value("몽이"))
                .andExpect(jsonPath("$.result.hasNonDogCatPet").value(false));
    }

    @Test
    @DisplayName("동반 조건 원문은 값이 없어도 키를 남긴다")
    void 동반_조건_원문은_값이_없어도_키를_남긴다() throws Exception {
        when(facilityQueryService.getFacilityDetail(eq(2L), any(), any(), any()))
                .thenReturn(createDetail());

        mockMvc.perform(get(DETAIL_PATH, 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.petConditionRaw").doesNotExist())
                .andExpect(content().string(containsString("\"petConditionRaw\":null")));
    }

    @Test
    @DisplayName("좌표를 보내지 않아도 상세 조회는 성공한다")
    void 좌표를_보내지_않아도_상세_조회는_성공한다() throws Exception {
        when(facilityQueryService.getFacilityDetail(eq(2L), any(), isNull(), isNull()))
                .thenReturn(createDetail());

        mockMvc.perform(get(DETAIL_PATH, 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    @DisplayName("상세 조회 위도가 범위를 벗어나면 400을 반환한다")
    void 상세_조회_위도가_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(get(DETAIL_PATH, 2L)
                        .param("latitude", "91.0")
                        .param("longitude", "128.9107"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.latitude").value("위도는 90 이하여야 합니다."));

        verifyNoInteractions(facilityQueryService);
    }

    @Test
    @DisplayName("상세 조회 경도가 범위를 벗어나면 400을 반환한다")
    void 상세_조회_경도가_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(get(DETAIL_PATH, 2L)
                        .param("latitude", "37.8016")
                        .param("longitude", "181.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.longitude").value("경도는 180 이하여야 합니다."));

        verifyNoInteractions(facilityQueryService);
    }

    // ------------------------------------------------------------------
    // 발자국 랭킹
    // ------------------------------------------------------------------

    private FacilityResponseDTO.RankingResult createRankingResult() {
        FacilityResponseDTO.RankingItem item = new FacilityResponseDTO.RankingItem(
                1,
                7L,
                "헤이도그 애견호텔&카페",
                FacilityCategory.CAFE,
                "강원특별자치도",
                "강릉시",
                1900L,
                PetAllowed.ALLOWED,
                new FacilityResponseDTO.PawGrade(5, "최고 등급"),
                96.2,
                160L
        );

        return new FacilityResponseDTO.RankingResult(List.of(item), 42L);
    }

    @Test
    @DisplayName("랭킹 조회에 성공하면 200과 순위 목록을 반환한다")
    void 랭킹_조회에_성공하면_200과_순위_목록을_반환한다() throws Exception {
        when(facilityQueryService.getRanking(any())).thenReturn(createRankingResult());

        mockMvc.perform(get(RANKING_PATH)
                        .param("sidoCode", "32")
                        .param("category", "CAFE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.total").value(42))
                .andExpect(jsonPath("$.result.items[0].rank").value(1))
                .andExpect(jsonPath("$.result.items[0].facilityId").value(7))
                .andExpect(jsonPath("$.result.items[0].sido").value("강원특별자치도"))
                .andExpect(jsonPath("$.result.items[0].distanceM").value(1900))
                .andExpect(jsonPath("$.result.items[0].pawGrade.level").value(5))
                .andExpect(jsonPath("$.result.items[0].pawGrade.label").value("최고 등급"))
                .andExpect(jsonPath("$.result.items[0].petScore").value(96.2))
                .andExpect(jsonPath("$.result.items[0].reviewCnt").value(160));
    }

    @Test
    @DisplayName("랭킹은 필터 없이도 조회된다")
    void 랭킹은_필터_없이도_조회된다() throws Exception {
        when(facilityQueryService.getRanking(any())).thenReturn(createRankingResult());

        mockMvc.perform(get(RANKING_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    @DisplayName("랭킹 조회에서 category가 정의된 값이 아니면 400을 반환한다")
    void 랭킹_조회에서_category가_정의된_값이_아니면_400을_반환한다() throws Exception {
        mockMvc.perform(get(RANKING_PATH).param("category", "HOTEL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.category").exists());

        verifyNoInteractions(facilityQueryService);
    }

    @Test
    @DisplayName("랭킹 조회에서 반경이 범위를 벗어나면 400을 반환한다")
    void 랭킹_조회에서_반경이_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(get(RANKING_PATH)
                        .param("latitude", "37.5665")
                        .param("longitude", "126.9780")
                        .param("radiusM", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.radiusM").exists());

        verifyNoInteractions(facilityQueryService);
    }

    // ------------------------------------------------------------------
    // 지역 목록
    // ------------------------------------------------------------------

    @Test
    @DisplayName("지역 목록 조회에 성공하면 200과 시도·시군구를 반환한다")
    void 지역_목록_조회에_성공하면_200과_시도_시군구를_반환한다() throws Exception {
        when(facilityQueryService.getRegions()).thenReturn(List.of(
                new FacilityResponseDTO.Region("32", "강원특별자치도", List.of(
                        new FacilityResponseDTO.Sigungu("1", "강릉시"),
                        new FacilityResponseDTO.Sigungu("5", "속초시")
                ))
        ));

        mockMvc.perform(get(REGIONS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result[0].sidoCode").value("32"))
                .andExpect(jsonPath("$.result[0].sido").value("강원특별자치도"))
                .andExpect(jsonPath("$.result[0].sigungus[0].sigunguCode").value("1"))
                .andExpect(jsonPath("$.result[0].sigungus[0].sigungu").value("강릉시"))
                .andExpect(jsonPath("$.result[0].sigungus[1].sigungu").value("속초시"));
    }

    @Test
    @DisplayName("지역 목록이 비어도 200과 빈 배열을 반환한다")
    void 지역_목록이_비어도_200과_빈_배열을_반환한다() throws Exception {
        when(facilityQueryService.getRegions()).thenReturn(List.of());

        mockMvc.perform(get(REGIONS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").isArray())
                .andExpect(jsonPath("$.result").isEmpty());
    }
}
