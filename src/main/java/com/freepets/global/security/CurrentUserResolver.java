package com.freepets.global.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * permitAll 경로에서 로그인 여부에 따라 개인화하려는 컨트롤러가 쓴다. 비로그인 요청은 Spring
 * Security의 {@code AnonymousAuthenticationToken}(principal이 {@code "anonymousUser"} 문자열)이
 * 채워지는데, {@code @AuthenticationPrincipal Long userId}로 그대로 받으면 이 문자열을 Long에
 * 대입하려다 타입 불일치로 500이 난다 — 그래서 {@link SecurityContextHolder}를 직접 보고
 * principal이 {@code Long}인 경우에만 값을 쓴다.
 */
public class CurrentUserResolver {

    private CurrentUserResolver() {}

    public static Long resolveOptionalUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof Long userId
                ? userId
                : null;
    }

}
