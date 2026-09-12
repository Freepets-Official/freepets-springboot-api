package com.freepets.global.security.jwt;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String JWT_EXCEPTION_ATTRIBUTE = "jwtException";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            try {
                Long userId = jwtProvider.getUserIdFromAccessToken(token);
                // 서명·만료는 유효해도, 그 사이 탈퇴한 계정이면 인증 단계에서 걸러낸다 — 이
                // 리포는 로그아웃 때도 서버 쪽 토큰 무효화가 없어서(순수 서명 검증), 여기서
                // 막지 않으면 탈퇴 후에도 토큰이 자연 만료될 때까지 다른 모든 도메인 API를
                // 계속 호출할 수 있다.
                if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
                    throw new GeneralException(ErrorStatus.MEMBER4007);
                }
                SecurityContextHolder.getContext().setAuthentication(createAuthentication(userId));
            } catch (GeneralException exception) {
                SecurityContextHolder.clearContext();
                request.setAttribute(JWT_EXCEPTION_ATTRIBUTE, exception);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private Authentication createAuthentication(Long userId) {
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new UsernamePasswordAuthenticationToken(userId, null, authorities);
    }
}
