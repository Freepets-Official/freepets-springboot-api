package com.freepets.domain.course.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freepets.domain.course.dto.CourseCheckResponseDTO;
import com.freepets.domain.course.service.CourseCheckService;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.petcheck.entity.PetCheckResult;

@WebMvcTest(CourseCheckController.class)
@AutoConfigureMockMvc(addFilters = false)
class CourseCheckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseCheckService courseCheckService;

    @Test
    @DisplayName("코스 일괄 검증에 성공하면 200과 스톱별 결과를 반환한다")
    void 코스_일괄_검증에_성공하면_200과_스톱별_결과를_반환한다() throws Exception {
        CourseCheckResponseDTO.CourseCheckResult result = new CourseCheckResponseDTO.CourseCheckResult(
                PetCheckResult.DENIED,
                1L,
                List.of(
                        new CourseCheckResponseDTO.Stop(
                                new CourseCheckResponseDTO.FacilitySummary(3L, "산책로", FacilityCategory.TOUR),
                                "10:00",
                                List.of(new CourseCheckResponseDTO.StopVerdict(1L, "몽이", PetCheckResult.ALLOWED, "모든 조건을 충족해 출입 가능합니다", List.of())),
                                PetCheckResult.ALLOWED,
                                null
                        ),
                        new CourseCheckResponseDTO.Stop(
                                new CourseCheckResponseDTO.FacilitySummary(7L, "카페", FacilityCategory.CAFE),
                                "11:30",
                                List.of(new CourseCheckResponseDTO.StopVerdict(2L, "보리", PetCheckResult.DENIED, "체중 초과", List.of())),
                                PetCheckResult.DENIED,
                                new CourseCheckResponseDTO.Alternative(15L, "대형견카페", 1.2)
                        )
                )
        );
        when(courseCheckService.checkCourse(isNull(), eq(List.of(1L, 2L)), eq(List.of(3L, 7L)))).thenReturn(result);

        mockMvc.perform(post("/api/v1/ai/course-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petIds\":[1,2],\"facilityIds\":[3,7]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.overall").value("DENIED"))
                .andExpect(jsonPath("$.result.blockedCount").value(1))
                .andExpect(jsonPath("$.result.stops[0].time").value("10:00"))
                .andExpect(jsonPath("$.result.stops[0].alternative").doesNotExist())
                .andExpect(jsonPath("$.result.stops[1].overall").value("DENIED"))
                .andExpect(jsonPath("$.result.stops[1].alternative.facilityId").value(15))
                .andExpect(jsonPath("$.result.stops[1].alternative.distanceKm").value(1.2));
    }

    @Test
    @DisplayName("facilityIds가 비어있으면 400을 반환한다")
    void facilityIds가_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/ai/course-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petIds\":[1],\"facilityIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.facilityIds").exists());

        verifyNoInteractions(courseCheckService);
    }

    @Test
    @DisplayName("petIds가 비어있으면 400을 반환한다")
    void petIds가_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/ai/course-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petIds\":[],\"facilityIds\":[3]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.petIds").exists());

        verifyNoInteractions(courseCheckService);
    }

}
