package com.freepets.global.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// SecurityFilterChain 동작 검증용 테스트 전용 컨트롤러. 프로덕션에는 포함되지 않는다.
@RestController
class SecurityTestPingController {

    @GetMapping("/api/v1/security-test/ping")
    public String ping(@AuthenticationPrincipal Long userId) {
        return "pong:" + userId;
    }
}
