package com.freepets.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

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
            // 소셜 로그인. 아직 우리 토큰이 없는 상태로 들어오므로 인증을 요구할 수 없다.
            "/api/v1/auth/social/*",
            // 동반 출입증 QR이 가리키는 공개 웹페이지 — 스캔하는 시설 직원은 앱 계정이 없다.
            "/verify/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/h2-console/**"
    };

    private final JwtProvider jwtProvider;
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
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);

        return httpSecurity.build();
    }
}
