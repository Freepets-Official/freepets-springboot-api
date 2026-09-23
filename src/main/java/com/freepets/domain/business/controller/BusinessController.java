package com.freepets.domain.business.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.service.BusinessCommandService;
import com.freepets.domain.business.service.BusinessQueryService;
import com.freepets.domain.business.service.FacilityOwnerClaimQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/business")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessQueryService businessQueryService;
    private final BusinessCommandService businessCommandService;
    private final FacilityOwnerClaimQueryService facilityOwnerClaimQueryService;

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
     * 신규 매장 등록 전 중복 후보 사전조회. 이름·주소를 입력한 시점에 바로 근처 유사 시설을 보여줘,
     * 등록 폼을 다 채우기 전에 "이 매장 아닌가요?" 확인을 띄울 수 있게 한다.
     */
    @PostMapping("/facilities/duplicate-check")
    public ApiResponse<BusinessResponseDTO.FacilityDuplicateCandidateList> duplicateCheck(
            @Valid @RequestBody BusinessRequestDTO.FacilityDuplicateCheckRequest request
    ) {
        return ApiResponse.onSuccess(
                businessQueryService.duplicateCheck(request)
        );
    }

    /**
     * 신규 매장 등록. 관광공사 목록에 없는 매장을 사업자가 직접 입력해 시설을 만들면서 동시에 소유권을
     * 갖는다. {@link #claim}과 달리 등록증 업로드·관리자 심사가 없어, 국세청 진위확인만 통과하면 즉시
     * 시설이 생기고 소유권이 확정된다.
     */
    @PostMapping("/facilities")
    public ApiResponse<BusinessResponseDTO.FacilityRegisterResult> registerFacility(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody BusinessRequestDTO.FacilityRegisterRequest request
    ) {
        return ApiResponse.onSuccess(
                businessCommandService.registerFacility(userId, request)
        );
    }

    /**
     * 매장 등록 신청. 사업자 정보를 다시 확인하고 사업자등록증을 올린 뒤, 운영자 승인을 기다리는 신청을 만든다.
     *
     * <p>접수일 뿐이라 이 시점에는 소유권도 사업자 프로필도 생기지 않고, 함께 받은 출입 조건도 시설에 반영되지
     * 않는다. 운영자가 등록증을 매장과 대조해 승인해야 반영된다.
     */
    @PostMapping(value = "/facilities/{facilityId}/claim", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BusinessResponseDTO.ClaimResult> claim(
            @AuthenticationPrincipal Long userId,
            @PathVariable("facilityId") Long facilityId,
            @Valid @ModelAttribute BusinessRequestDTO.ClaimRequest request
    ) {
        return ApiResponse.onSuccess(
                businessCommandService.claim(userId, facilityId, request)
        );
    }

    /**
     * 내 매장 등록 신청 목록. 앱의 "심사 중" 화면이 이 API로 상태를 보여준다.
     */
    @GetMapping("/claims")
    public ApiResponse<BusinessResponseDTO.MyClaimList> myClaims(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.onSuccess(
                facilityOwnerClaimQueryService.getMyClaims(userId)
        );
    }
}
