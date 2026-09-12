package com.freepets.domain.business.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.service.BusinessCommandService;
import com.freepets.domain.business.service.BusinessQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/business")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessQueryService businessQueryService;
    private final BusinessCommandService businessCommandService;

    /**
     * 사업자 인증. 국세청 진위확인 결과만 돌려주고 <b>아무것도 저장하지 않는다</b> —
     * 소유 기록은 매장 등록 단계에서 만든다.
     *
     * <p>요청자를 따로 받지 않는다. 저장하는 값이 없어 누구의 요청인지 쓸 데가 없고,
     * 로그인 여부는 시큐리티 설정이 이미 요구한다.
     */
    @PostMapping("/verify")
    public ApiResponse<BusinessResponseDTO.VerifyResult> verify(
            @Valid @RequestBody BusinessRequestDTO.VerifyRequest request
    ) {
        return ApiResponse.onSuccess(
                businessQueryService.verify(request)
        );
    }

    /**
     * 매장 등록. 사업자 정보를 다시 확인해 소유 기록을 만들고, 함께 받은 출입 조건을 확정한다.
     * 등록이 성공하면 그 계정에 사업자 프로필이 생긴다.
     */
    @PostMapping("/facilities/{facilityId}/claim")
    public ApiResponse<BusinessResponseDTO.ClaimResult> claim(
            @AuthenticationPrincipal Long userId,
            @PathVariable("facilityId") Long facilityId,
            @Valid @RequestBody BusinessRequestDTO.ClaimRequest request
    ) {
        return ApiResponse.onSuccess(
                businessCommandService.claim(userId, facilityId, request)
        );
    }
}
