package com.freepets.domain.gamification.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.gamification.dto.GamificationResponseDTO;
import com.freepets.domain.gamification.service.RankingQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * {@code GamificationController}(={@code /api/v1/me/gamification})와 base가 다르다 —
 * 랭킹은 "내 것"이 아니라 다른 유저들과 비교하는 화면이라 {@code /me} 하위에 두지 않는다.
 * 같은 컨트롤러에 두면 클래스 레벨 {@code @RequestMapping}이 그대로 앞에 붙어버려서
 * {@code /me/gamification/ranking}이 돼버린다.
 */
@RestController
@RequestMapping("/api/v1/gamification")
@RequiredArgsConstructor
public class GamificationRankingController {

    private final RankingQueryService rankingQueryService;

    /**
     * 게스트(토큰 없음)도 호출할 수 있다 — 이때 {@code userId}는 null로 들어오고, 응답의
     * {@code me}가 그대로 생략된다(SecurityConfig의 GUEST_GET_PATTERNS 참고).
     *
     * <p>지금은 전국 스코프만 있다 — 시/도·시/군/구 스코프 파라미터는 아직 안 받는다.
     */
    @GetMapping("/ranking")
    public ApiResponse<GamificationResponseDTO.RankingResult> getRanking(
            @AuthenticationPrincipal Long userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ApiResponse.onSuccess(
                rankingQueryService.getNationalRanking(userId, page, size)
        );
    }

}
