package com.freepets.domain.facility.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
}
