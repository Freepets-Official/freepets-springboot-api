package com.freepets.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.freepets.domain.user.entity.Role;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.security.JwtAccessDeniedHandler;
import com.freepets.global.security.JwtAuthenticationEntryPoint;
import com.freepets.global.security.jwt.JwtAuthenticationFilter;
import com.freepets.global.security.jwt.JwtProvider;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PERMIT_ALL_PATTERNS = {
            "/api/v1/users/signup",
            "/api/v1/users/login",
            // 07-courses.md — 로그인 전에도 쓸 수 있는 코스 모드. regions/distance-options는
            // preset의 지역·거리 슬라이더를 채우는 용도라 같이 열어둔다.
            "/api/v1/courses/preset",
            "/api/v1/courses/regions",
            "/api/v1/courses/themes",
            "/api/v1/courses/distance-options",
            // 다른 사용자가 공개한 코스 둘러보기 — 로그인 전에도 담아갈 마음이 들게 열어둔다.
            "/api/v1/courses/public",
            // stopId 순서만 재배열하는 순수 계산이라 userId 자체를 안 쓴다(CourseController 참고)
            // — 로그인 여부와 무관하게 열려있어야 한다.
            "/api/v1/courses/optimize-order",
            // 1.0 앱이 로그인 여부와 무관하게 항상 보여주던 하드코딩 공지를 대체하는 API라
            // 마찬가지로 로그인 전에도 열려있어야 한다(Notice 엔티티 참고).
            "/api/v1/notices",
            // 소셜 로그인. 아직 우리 토큰이 없는 상태로 들어오므로 인증을 요구할 수 없다.
            "/api/v1/auth/social/*",
            // 액세스 토큰이 만료된 상태로 들어오는 요청이라 인증을 요구할 수 없다.
            // 신원 확인은 헤더로 받은 리프레시 토큰으로 서비스에서 직접 한다.
            "/api/v1/auth/refresh",
            // 동반 출입증 QR이 가리키는 공개 웹페이지 — 스캔하는 시설 직원은 앱 계정이 없다.
            "/verify/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/h2-console/**"
    };

    /**
     * 게스트 모드 — 로그인 없이도 시설을 탐색할 수 있어야 한다. 토큰이 있으면 각 서비스가
     * {@code @AuthenticationPrincipal}로 받은 userId로 개인화(내 리뷰·반려동물 궁합 등)를 얹고,
     * 없으면 공개 데이터만 내려준다. 리뷰·거부 제보 작성 등 쓰기 API는 대상이 아니다.
     *
     * <p>그래서 경로가 아니라 GET에만 연다 — 경로만으로 열면 같은 경로에 걸린 쓰기 API까지
     * 같이 열린다. 실제로 {@code POST /facilities/{facilityId}/reviews}(리뷰 작성)가 목록
     * 조회와 경로가 같아서 함께 열려 있었고, 토큰이 없거나 만료된 요청이 401 대신 userId가
     * null인 채로 컨트롤러까지 들어갔다.
     *
     * <p>{@code {facilityId}}는 숫자로 제한해서 연다 — {@code "*"}로 열면 같은 depth의 문자열
     * 경로가 의도와 무관하게 함께 열린다. 여는 경로는 하나씩 적어서 연다.
     */
    private static final String[] GUEST_GET_PATTERNS = {
            "/api/v1/facilities",
            "/api/v1/facilities/ranking",
            // 랭킹·전체 목록의 지역 칩을 그리는 목록이다. 응답이 법정동 코드와 지역명뿐이라
            // 사용자마다 달라지지 않는다. 이게 막혀 있으면 게스트는 지역을 고를 수 없다.
            "/api/v1/facilities/regions",
            "/api/v1/facilities/{facilityId:[0-9]+}",
            "/api/v1/facilities/*/reviews",
            "/api/v1/facilities/*/denial-reports/recent",
            // 다른 유저와 비교하는 화면이라 개인화가 없어도 볼 수 있어야 한다 — 토큰 없으면
            // 응답의 me가 생략된다(GamificationRankingController 참고).
            "/api/v1/gamification/ranking"
    };

    // 시설 검색도 같은 게스트 모드 읽기 API지만, 조건이 많아 본문으로 받느라 POST다
    // (FacilityController 참고) — GET 목록에 넣을 수 없어 따로 연다.
    private static final String FACILITY_SEARCH_PATTERN = "/api/v1/facilities/search";

    // 운영자 전용 API. 관리자 판정은 이 규칙 한 곳에만 둔다 — 나중에 관리자 계정을 분리하더라도
    // 여기만 바꾸면 된다.
    private static final String ADMIN_PATTERN = "/api/v1/admin/**";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PERMIT_ALL_PATTERNS).permitAll()
                        .requestMatchers(HttpMethod.GET, GUEST_GET_PATTERNS).permitAll()
                        .requestMatchers(HttpMethod.POST, FACILITY_SEARCH_PATTERN).permitAll()
                        .requestMatchers(ADMIN_PATTERN).access(requireAdminRole())
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, userRepository), UsernamePasswordAuthenticationFilter.class);

        return httpSecurity.build();
    }

    /**
     * 관리자 전용 경로에서만 역할을 DB로 확인한다. {@code JwtAuthenticationFilter}는 역할을 싣지
     * 않으므로(그 필터는 모든 인증 요청에서 돈다 — 역할 조회에 문제가 생기면 앱 전체가 영향을
     * 받는다), 여기서 인증된 사용자의 userId로 직접 조회한다.
     *
     * <p>익명 요청(토큰 없음)에 대한 거부는 {@code ExceptionTranslationFilter}가 401로,
     * 인증됐지만 관리자가 아닌 거부는 403으로 갈린다 — 이 판단은 인증 객체가 익명인지 여부로만
     * 갈리므로 {@code hasRole(...)}을 쓸 때와 동일하게 동작한다.
     */
    private AuthorizationManager<RequestAuthorizationContext> requireAdminRole() {
        return (authentication, context) -> {
            boolean isAdmin = authentication.get().getPrincipal() instanceof Long userId
                    && userRepository.findActiveRoleById(userId)
                            .map(role -> role == Role.ADMIN)
                            .orElse(false);
            return new AuthorizationDecision(isAdmin);
        };
    }
}
