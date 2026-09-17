package com.freepets.domain.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freepets.domain.review.dto.ReviewResponseDTO;
import com.freepets.domain.review.service.ReviewCommandService;
import com.freepets.domain.review.service.ReviewQueryService;

/**
 * 리뷰 작성/수정 API가 사진 첨부(멀티파트)와 기존 JSON 호출을 동시에 받을 수 있는지 확인한다.
 * consumes가 다른 두 메소드를 같은 경로에 뒀을 때 Content-Type에 따라 실제로 잘 갈리는지가
 * 코드 리뷰에서 나온 우려라 여기서 직접 HTTP 레벨로 검증한다.
 */
@WebMvcTest(ReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewCommandService reviewCommandService;

    @MockitoBean
    private ReviewQueryService reviewQueryService;

    private ReviewResponseDTO.UpsertResult upsertResult(String photoUrl) {
        return new ReviewResponseDTO.UpsertResult(
                1L, 7L, List.of(1L), true, 5, 5, 5, "좋았어요", List.of(), LocalDate.now(), photoUrl
        );
    }

    @Test
    @DisplayName("멀티파트로 사진을 첨부해 리뷰를 작성하면 200과 photoUrl을 받는다")
    void 멀티파트로_사진을_첨부해_작성하면_200() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("photo", "visit.jpg", "image/jpeg", "content".getBytes());
        when(reviewCommandService.upsertReview(any(), eq(7L), any()))
                .thenReturn(upsertResult("https://s3/visit.jpg"));

        mockMvc.perform(multipart("/api/v1/facilities/{facilityId}/reviews", 7L)
                        .file(photo)
                        .param("petIds", "1")
                        .param("ratingSpace", "5")
                        .param("ratingStaff", "5")
                        .param("ratingAmenity", "5")
                        .param("content", "좋았어요"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.photoUrl").value("https://s3/visit.jpg"));
    }

    @Test
    @DisplayName("사진 없이 JSON으로 리뷰를 작성해도(기존 클라이언트 호환) 200을 받는다")
    void JSON으로_사진없이_작성해도_200() throws Exception {
        when(reviewCommandService.upsertReview(any(), eq(7L), any()))
                .thenReturn(upsertResult(null));

        String requestBody = """
                {"petIds":[1],"ratingSpace":5,"ratingStaff":5,"ratingAmenity":5,"content":"좋았어요"}
                """;

        mockMvc.perform(post("/api/v1/facilities/{facilityId}/reviews", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.photoUrl").doesNotExist());
    }

    @Test
    @DisplayName("멀티파트로 사진을 교체해 리뷰를 수정하면 200과 새 photoUrl을 받는다")
    void 멀티파트로_사진을_교체해_수정하면_200() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("photo", "visit2.jpg", "image/jpeg", "content".getBytes());
        when(reviewCommandService.updateReview(any(), eq(1L), any()))
                .thenReturn(upsertResult("https://s3/visit2.jpg"));

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/reviews/{reviewId}", 1L)
                        .file(photo)
                        .param("petIds", "1")
                        .param("ratingSpace", "5")
                        .param("ratingStaff", "5")
                        .param("ratingAmenity", "5")
                        .param("content", "좋았어요"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.photoUrl").value("https://s3/visit2.jpg"));
    }

    @Test
    @DisplayName("사진 없이 JSON으로 리뷰를 수정해도(기존 클라이언트 호환) 200을 받는다")
    void JSON으로_사진없이_수정해도_200() throws Exception {
        when(reviewCommandService.updateReview(any(), eq(1L), any()))
                .thenReturn(upsertResult(null));

        String requestBody = """
                {"petIds":[1],"ratingSpace":5,"ratingStaff":5,"ratingAmenity":5,"content":"좋았어요"}
                """;

        mockMvc.perform(put("/api/v1/reviews/{reviewId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.photoUrl").doesNotExist());
    }
}
