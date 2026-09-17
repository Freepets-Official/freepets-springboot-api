package com.freepets.global.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freepets.domain.auth.controller.AuthController;
import com.freepets.domain.auth.dto.AuthResponseDTO;
import com.freepets.domain.auth.service.AuthCommandService;
import com.freepets.domain.course.controller.CourseController;
import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.service.CourseCommandService;
import com.freepets.domain.course.service.CourseLikedService;
import com.freepets.domain.course.service.CoursePresetService;
import com.freepets.domain.course.service.CourseQueryService;
import com.freepets.domain.course.service.CourseSimilarService;
import com.freepets.domain.facility.controller.FacilityController;
import com.freepets.domain.facility.dto.FacilityResponseDTO;
import com.freepets.domain.facility.service.FacilityQueryService;
import com.freepets.domain.report.controller.DenialReportController;
import com.freepets.domain.report.service.DenialReportCommandService;
import com.freepets.domain.report.service.DenialReportQueryService;
import com.freepets.domain.review.controller.ReviewController;
import com.freepets.domain.review.service.ReviewCommandService;
import com.freepets.domain.review.service.ReviewQueryService;
import com.freepets.domain.user.controller.UserController;
import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.entity.Role;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.domain.user.service.UserCommandService;
import com.freepets.domain.user.service.UserQueryService;
import com.freepets.global.config.SecurityConfig;
import com.freepets.global.config.JwtConfig;
import com.freepets.global.security.jwt.JwtProvider;

@WebMvcTest(controllers = {
        UserController.class,
        AuthController.class,
        CourseController.class,
        FacilityController.class,
        ReviewController.class,
        DenialReportController.class,
        SecurityTestPingController.class
})
@Import({SecurityConfig.class, JwtConfig.class, JwtProvider.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private UserCommandService userCommandService;

    @MockitoBean
    private UserQueryService userQueryService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthCommandService authCommandService;

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

    @MockitoBean
    private FacilityQueryService facilityQueryService;

    @MockitoBean
    private ReviewQueryService reviewQueryService;

    @MockitoBean
    private ReviewCommandService reviewCommandService;

    @MockitoBean
    private DenialReportQueryService denialReportQueryService;

    @MockitoBean
    private DenialReportCommandService denialReportCommandService;

    @Test
    void 토큰없이_보호된_경로_요청시_401과_COMMON401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/security-test/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401"));
    }

    @Test
    void 잘못된_토큰으로_보호된_경로_요청시_401과_TOKEN4001을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/security-test/ping")
                        .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("TOKEN4001"));
    }

    @Test
    void 유효한_토큰으로_보호된_경로_요청시_200을_반환한다() throws Exception {
        String token = jwtProvider.createAccessToken(1L);
        when(userRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(true);

        mockMvc.perform(get("/api/v1/security-test/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("pong:1"));
    }

    // 탈퇴 직후에도 아직 만료되지 않은 토큰으로 다른 모든 도메인 API를 계속 호출할 수 있던
    // 문제(각 서비스가 저마다 findById를 쓰던 것) — 인증 경계에서 한 번에 막는지 확인한다.
    @Test
    void 탈퇴한_유저의_토큰으로_보호된_경로_요청시_401과_MEMBER4007을_반환한다() throws Exception {
        String token = jwtProvider.createAccessToken(1L);
        when(userRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(false);

        mockMvc.perform(get("/api/v1/security-test/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("MEMBER4007"));
    }

    @Test
    void 토큰없이_관리자_경로_요청시_401과_COMMON401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/security-test/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401"));
    }

    @Test
    void 일반_사용자_토큰으로_관리자_경로_요청시_403과_COMMON403을_반환한다() throws Exception {
        String token = jwtProvider.createAccessToken(1L);
        when(userRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(true);
        when(userRepository.findActiveRoleById(1L)).thenReturn(Optional.of(Role.USER));

        mockMvc.perform(get("/api/v1/admin/security-test/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON403"));
    }

    @Test
    void 관리자_토큰으로_관리자_경로_요청시_200을_반환한다() throws Exception {
        String token = jwtProvider.createAccessToken(2L);
        when(userRepository.existsByIdAndDeletedAtIsNull(2L)).thenReturn(true);
        when(userRepository.findActiveRoleById(2L)).thenReturn(Optional.of(Role.ADMIN));

        mockMvc.perform(get("/api/v1/admin/security-test/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-pong:2"));
    }

    // 관리자 역할은 일반 경로 접근을 막지 않아야 한다 — 운영자도 자기 앱 계정으로 앱 기능을 쓴다.
    // 일반 경로는 관리자 판정을 아예 거치지 않으므로 findActiveRoleById는 부르지 않는다.
    @Test
    void 관리자_토큰으로_일반_보호_경로_요청시_200을_반환한다() throws Exception {
        String token = jwtProvider.createAccessToken(2L);
        when(userRepository.existsByIdAndDeletedAtIsNull(2L)).thenReturn(true);

        mockMvc.perform(get("/api/v1/security-test/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("pong:2"));
    }

    @Test
    void signup_로그인_경로는_토큰없이도_통과한다() throws Exception {
        when(userCommandService.signUp(any())).thenReturn(new UserResponseDTO.SignUpResult());

        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@test.com\",\"password\":\"password1\",\"nickname\":\"tester\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 소셜_로그인_경로는_토큰없이도_통과한다() throws Exception {
        // 아직 우리 토큰이 없는 상태로 들어오는 경로라 인증을 요구하면 로그인 자체가 불가능하다.
        when(authCommandService.socialLogin(any(), any()))
                .thenReturn(new AuthResponseDTO.SocialLoginResult("1", "access", "refresh", true));

        mockMvc.perform(post("/api/v1/auth/social/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerToken\":\"provider-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.isNewUser").value(true));
    }

    // 프론트 연동 중 발견 — regions/themes는 permitAll에 있었는데 distance-options만 빠져있어서
    // 401이 났다(CourseController의 "로그인 불필요" 주석과 실제 SecurityConfig가 어긋나 있었음).
    @Test
    void 거리_옵션_조회는_토큰없이도_통과한다() throws Exception {
        when(coursePresetService.getDistanceOptions())
                .thenReturn(new CourseResponseDTO.DistanceOptionList(List.of()));

        mockMvc.perform(get("/api/v1/courses/distance-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    // optimizeOrder는 courseCommandService.optimizeOrder(stopIds)가 userId를 아예 안 받는 순수
    // 계산이라 로그인 여부와 무관하게 열려있어야 하는데, 이것도 permitAll에서 빠져 401이 났다.
    @Test
    void 경로_최적화는_토큰없이도_통과한다() throws Exception {
        when(courseCommandService.optimizeOrder(any()))
                .thenReturn(new CourseResponseDTO.OrderResult(List.of(1L, 2L)));

        mockMvc.perform(post("/api/v1/courses/optimize-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stopIds\":[2,1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    // 게스트 모드 — "로그인 없이도 시설 탐색은 돼야 한다"는 요구사항으로 연 5개 읽기 API가
    // 실제로 토큰 없이 통과하는지 확인한다. 각 서비스는 @AuthenticationPrincipal이 null로 넘겨준
    // userId를 그대로 받는다.
    @Test
    void 시설_검색은_토큰없이도_통과한다() throws Exception {
        when(facilityQueryService.searchFacilities(any()))
                .thenReturn(new FacilityResponseDTO.FacilitySearchResult(List.of(), 0));

        mockMvc.perform(post("/api/v1/facilities/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5,\"longitude\":127.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    void 시설_상세_조회는_토큰없이도_통과한다() throws Exception {
        when(facilityQueryService.getFacilityDetail(any(), any(), any(), any()))
                .thenReturn(null);

        mockMvc.perform(get("/api/v1/facilities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    void 발자국_랭킹_조회는_토큰없이도_통과한다() throws Exception {
        when(facilityQueryService.getRanking(any()))
                .thenReturn(new FacilityResponseDTO.RankingResult(List.of(), 0));

        mockMvc.perform(get("/api/v1/facilities/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    void 시설_리뷰_목록_조회는_토큰없이도_통과한다() throws Exception {
        when(reviewQueryService.getReviews(any(), any(), anyInt(), anyInt()))
                .thenReturn(null);

        mockMvc.perform(get("/api/v1/facilities/1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    void 최근_거부_제보_조회는_토큰없이도_통과한다() throws Exception {
        when(denialReportQueryService.getRecent(any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/facilities/1/denial-reports/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    // {facilityId}를 숫자 전용 패턴으로 열었기 때문에 같은 depth의 문자열 경로(regions)까지
    // 함께 열리지 않는지 확인한다 — 이번 요구사항 범위 밖이라 계속 인증이 필요해야 한다.
    @Test
    void 시설_지역_목록_조회는_여전히_토큰이_필요하다() throws Exception {
        mockMvc.perform(get("/api/v1/facilities/regions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401"));
    }

    // requestMatchers(String...)는 HTTP 메소드를 가리지 않아서, 리뷰 목록 조회(GET)만 게스트에게
    // 열려고 "/api/v1/facilities/*/reviews"를 문자열 패턴에 두면 같은 경로의 리뷰 작성(POST)까지
    // 같이 열려버린다. GET만 permitAll인지, POST는 여전히 인증이 필요한지 둘 다 확인한다.
    @Test
    void 리뷰_작성은_토큰없이_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/facilities/1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401"));
    }
}
