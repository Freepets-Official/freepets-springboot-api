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
            // 게스트 모드(로그인 없이 시설 탐색) — 애플 심사 요건. 토큰이 있으면 지금처럼
            // 개인화되고(CurrentUserResolver), 없으면 공개 데이터만 내려간다.
            // "/api/v1/facilities/*"는 한 세그먼트짜리 경로 전부(검색 아님·상세·랭킹)를 묶어서
            // 잡는다 — 대신 /facilities/regions는 이 목록에 없어야 계속 인증이 필요한데, 아래
            // securityFilterChain에서 이 permitAll보다 먼저 그 경로를 authenticated()로 명시해
            // 순서상 막아둔다(안 그러면 이 와일드카드에 걸려 같이 열려버린다).
            //
            // "/api/v1/facilities/*/reviews"는 여기 안 넣는다 — 이 패턴은 메소드를 안 가려서
            // GET(목록 조회)뿐 아니라 같은 경로의 POST(리뷰 작성, ReviewController.upsertReview)까지
            // permitAll로 열어버린다. 그 POST는 여전히 @AuthenticationPrincipal Long userId로
            // 본인 확인을 하므로, 익명 요청이 그대로 들어오면 익명 principal("anonymousUser"
            // 문자열)을 Long에 바인딩하려다 500이 난다 — 인증 경계가 뚫리는 게 아니라 깨지는
            // 것뿐이지만 의도한 동작이 아니다. 그래서 GET만 메소드로 제한해서 아래
            // securityFilterChain에 따로 등록한다.
            "/api/v1/facilities/*",
            "/api/v1/facilities/*/denial-reports/recent",
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
                        // "/api/v1/facilities/*"(permitAll)보다 먼저 선언해야 한다 — authorizeHttpRequests는
                        // 먼저 매치되는 규칙이 이기므로, 이 줄이 없으면 regions도 그 와일드카드에
                        // 걸려 같이 열려버린다. regions는 이번 게스트 모드 요청 범위에 없다.
                        .requestMatchers("/api/v1/facilities/regions").authenticated()
                        // GET만 연다 — 같은 경로의 POST(리뷰 작성)는 계속 인증이 필요하다
                        // (PERMIT_ALL_PATTERNS의 "/api/v1/facilities/*/reviews" 주석 참고).
                        .requestMatchers(HttpMethod.GET, "/api/v1/facilities/*/reviews").permitAll()
                        .requestMatchers(PERMIT_ALL_PATTERNS).permitAll()
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
