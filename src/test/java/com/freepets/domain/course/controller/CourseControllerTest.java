package com.freepets.domain.course.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.CourseTheme;
import com.freepets.domain.course.service.CourseCommandService;
import com.freepets.domain.course.service.CourseLikedService;
import com.freepets.domain.course.service.CoursePresetService;
import com.freepets.domain.course.service.CourseQueryService;
import com.freepets.domain.course.service.CourseSimilarService;
import com.freepets.domain.facility.entity.FacilityCategory;

@WebMvcTest(CourseController.class)
@AutoConfigureMockMvc(addFilters = false)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseLikedService courseLikedService;

    @MockitoBean
    private CourseSimilarService courseSimilarService;

    @MockitoBean
    private CoursePresetService coursePresetService;

    @MockitoBean
    private CourseQueryService courseQueryService;

    @MockitoBean
    private CourseCommandService courseCommandService;

    // ------------------------------------------------------------------
    // GET /courses/preset
    // ------------------------------------------------------------------

    @Test
    @DisplayName("preset 조회에 성공하면 200과 코스를 반환한다")
    void preset_조회에_성공하면_200과_코스를_반환한다() throws Exception {
        CourseResponseDTO.PresetCourseResult result = new CourseResponseDTO.PresetCourseResult(
                101L, "강릉시 애견 카페 코스",
                List.of(new CourseResponseDTO.PresetStop(1L, "카페A", FacilityCategory.CAFE, 88.4, 0.0))
        );
        when(coursePresetService.getPreset(eq("강원"), eq("강릉시"), eq(CourseTheme.PET_CAFE))).thenReturn(result);

        mockMvc.perform(get("/api/v1/courses/preset")
                        .param("sido", "강원")
                        .param("sigungu", "강릉시")
                        .param("theme", "PET_CAFE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.courseId").value(101))
                .andExpect(jsonPath("$.result.title").value("강릉시 애견 카페 코스"))
                .andExpect(jsonPath("$.result.stops[0].facilityId").value(1))
                .andExpect(jsonPath("$.result.stops[0].category").value("CAFE"));
    }

    @Test
    @DisplayName("sido가 없으면 400을 반환한다")
    void sido가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/courses/preset").param("theme", "PET_CAFE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(coursePresetService);
    }

    @Test
    @DisplayName("theme이 정의된 값이 아니면 400과 허용값 안내를 반환한다")
    void theme이_정의된_값이_아니면_400과_허용값_안내를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/courses/preset").param("sido", "강원").param("theme", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.theme").exists());

        verifyNoInteractions(coursePresetService);
    }

    @Test
    @DisplayName("theme이 없으면 400을 반환한다")
    void theme이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/courses/preset").param("sido", "강원"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(coursePresetService);
    }

    // ------------------------------------------------------------------
    // GET /courses/liked, /courses/similar
    // ------------------------------------------------------------------

    @Test
    @DisplayName("liked 조회에 성공하면 200을 반환한다")
    void liked_조회에_성공하면_200을_반환한다() throws Exception {
        CourseResponseDTO.LikedCourseResult result = new CourseResponseDTO.LikedCourseResult(
                "몽이가 좋아한 곳",
                List.of(new CourseResponseDTO.LikedStop(1L, "카페A", FacilityCategory.CAFE, 9.4, List.of()))
        );
        when(courseLikedService.getLikedCourse(isNull(), eq(List.of(1L, 2L)))).thenReturn(result);

        mockMvc.perform(get("/api/v1/courses/liked").param("petIds", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.title").value("몽이가 좋아한 곳"));
    }

    @Test
    @DisplayName("liked petIds가 없으면 400을 반환한다")
    void liked_petIds가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/courses/liked"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(courseLikedService);
    }

    @Test
    @DisplayName("similar 조회에 성공하면 200을 반환한다")
    void similar_조회에_성공하면_200을_반환한다() throws Exception {
        CourseResponseDTO.SimilarCourseResult result = new CourseResponseDTO.SimilarCourseResult(
                "취향과 비슷한 새로운 곳", List.of()
        );
        when(courseSimilarService.getSimilarCourse(isNull(), eq(List.of(1L)))).thenReturn(result);

        mockMvc.perform(get("/api/v1/courses/similar").param("petIds", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.title").value("취향과 비슷한 새로운 곳"));
    }

    // ------------------------------------------------------------------
    // 내 코스(CUSTOM) CRUD
    // ------------------------------------------------------------------

    @Test
    @DisplayName("내 코스 목록 조회에 성공하면 200을 반환한다")
    void 내_코스_목록_조회에_성공하면_200을_반환한다() throws Exception {
        when(courseQueryService.getMyCourses(isNull())).thenReturn(List.of(
                new CourseResponseDTO.MyCourse(10L, "몽이 코스", null, List.of(1L, 2L), LocalDateTime.now())
        ));

        mockMvc.perform(get("/api/v1/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].courseId").value(10))
                .andExpect(jsonPath("$.result[0].stopIds[0]").value(1));
    }

    @Test
    @DisplayName("코스 생성에 성공하면 200을 반환한다")
    void 코스_생성에_성공하면_200을_반환한다() throws Exception {
        when(courseCommandService.createCourse(isNull(), any()))
                .thenReturn(new CourseResponseDTO.MyCourse(10L, "몽이 코스", "설명", List.of(1L, 2L), LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"몽이 코스\",\"description\":\"설명\",\"stopIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.courseId").value(10));
    }

    @Test
    @DisplayName("코스 이름이 없으면 400을 반환한다")
    void 코스_이름이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stopIds\":[1,2]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.name").exists());

        verifyNoInteractions(courseCommandService);
    }

    @Test
    @DisplayName("스톱이 비어있으면 400을 반환한다")
    void 스톱이_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"몽이 코스\",\"stopIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.stopIds").exists());

        verifyNoInteractions(courseCommandService);
    }

    @Test
    @DisplayName("코스 수정에 성공하면 200을 반환한다")
    void 코스_수정에_성공하면_200을_반환한다() throws Exception {
        when(courseCommandService.updateCourse(isNull(), eq(10L), any()))
                .thenReturn(new CourseResponseDTO.MyCourse(10L, "변경된 이름", null, List.of(3L), LocalDateTime.now()));

        mockMvc.perform(put("/api/v1/courses/{courseId}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"변경된 이름\",\"stopIds\":[3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("변경된 이름"));
    }

    @Test
    @DisplayName("코스 삭제에 성공하면 200과 삭제된 courseId를 반환한다")
    void 코스_삭제에_성공하면_200과_삭제된_courseId를_반환한다() throws Exception {
        when(courseCommandService.deleteCourse(isNull(), eq(10L)))
                .thenReturn(new CourseResponseDTO.DeleteResult(10L));

        mockMvc.perform(delete("/api/v1/courses/{courseId}", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.courseId").value(10));
    }

}
