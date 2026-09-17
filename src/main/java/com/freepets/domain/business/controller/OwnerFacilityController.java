package com.freepets.domain.business.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.service.OwnerFacilityBenefitCommandService;
import com.freepets.domain.business.service.OwnerFacilityConditionCommandService;
import com.freepets.domain.business.service.OwnerFacilityProfileCommandService;
import com.freepets.domain.business.service.OwnerFacilityQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 사업자 대시보드. 등록이 끝난 매장을 관리하는 화면이라, 등록 신청을 받는 {@link BusinessController}와
 * 화면도 단계도 달라 컨트롤러를 분리한다({@link BusinessAdminController}와 같은 방식이다).
 *
 * <p>프로필(소비자/사업자)은 화면 세트일 뿐 권한이 아니다. 앱이 지금 어느 프로필을 쓰는지는 보내지 않고,
 * 서버가 요청마다 소유 기록으로 주인인지 판단한다. 그래서 소비자 프로필 상태에서 호출해도 소유자라면
 * 정상 응답이다 — 의도된 동작이다.
 *
 * <p>시설 ID를 경로로 받는 API를 여기 추가할 때는 서비스가 {@code FacilityOwnershipValidator}를
 * 부르는지 반드시 확인한다. 아래 내 매장 목록은 조회가 요청자의 소유 기록에서 출발해 남의 매장이 섞일
 * 수 없는 경우라 검증기를 거치지 않는다.
 */
@RestController
@RequestMapping("/api/v1/owner")
@RequiredArgsConstructor
public class OwnerFacilityController {

    private final OwnerFacilityQueryService ownerFacilityQueryService;
    private final OwnerFacilityConditionCommandService ownerFacilityConditionCommandService;
    private final OwnerFacilityProfileCommandService ownerFacilityProfileCommandService;
    private final OwnerFacilityBenefitCommandService ownerFacilityBenefitCommandService;

    /**
     * 내 매장 목록. 대시보드 첫 화면이 이 API로 매장 카드를 그리고, 사장님이 카드를 골라 관리 대상을 정한다.
     * "대표 매장" 개념은 없다 — 이후 호출은 고른 시설 ID를 경로에 싣는다.
     */
    @GetMapping("/facilities")
    public ApiResponse<BusinessResponseDTO.OwnerFacilityList> getMyFacilities(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.onSuccess(
                ownerFacilityQueryService.getMyFacilities(userId)
        );
    }

    /**
     * 거부 제보 전체 조회. 홈의 경고 카드를 탭했을 때 들어가는 화면으로, 확정 이후 실시간 거부
     * 제보를 최신순으로 전부 보여준다.
     */
    @GetMapping("/facilities/{facilityId}/denial-alerts")
    public ApiResponse<BusinessResponseDTO.DenialAlertList> getDenialAlerts(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long facilityId
    ) {
        return ApiResponse.onSuccess(
                ownerFacilityQueryService.getDenialAlerts(userId, facilityId)
        );
    }

    /**
     * 출입 조건 수정. 재심사 없이 즉시 반영한다 — 이미 등록증 대조를 통과한 소유자이기 때문이다.
     */
    @PutMapping("/facilities/{facilityId}/conditions")
    public ApiResponse<BusinessResponseDTO.EntryCondition> updateConditions(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long facilityId,
            @Valid @RequestBody BusinessRequestDTO.ConditionUpdateRequest request
    ) {
        return ApiResponse.onSuccess(
                ownerFacilityConditionCommandService.updateConditions(userId, facilityId, request)
        );
    }

    /**
     * 매장 소개·홍보 저장. 소개글과 편의시설 태그를 화면 하단 "저장하기" 하나로 함께 반영한다.
     */
    @PutMapping("/facilities/{facilityId}/profile")
    public ApiResponse<BusinessResponseDTO.FacilityProfile> updateProfile(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long facilityId,
            @Valid @RequestBody BusinessRequestDTO.FacilityProfileUpdateRequest request
    ) {
        return ApiResponse.onSuccess(
                ownerFacilityProfileCommandService.updateProfile(userId, facilityId, request)
        );
    }

    /**
     * 방문 혜택 관리 화면 목록. on/off 상관없이 등록순으로 전부 보여준다.
     */
    @GetMapping("/facilities/{facilityId}/benefits")
    public ApiResponse<BusinessResponseDTO.VisitBenefitList> getBenefits(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long facilityId
    ) {
        return ApiResponse.onSuccess(
                ownerFacilityQueryService.getBenefits(userId, facilityId)
        );
    }

    /**
     * 방문 혜택 추가.
     */
    @PostMapping("/facilities/{facilityId}/benefits")
    public ApiResponse<BusinessResponseDTO.VisitBenefit> createBenefit(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long facilityId,
            @Valid @RequestBody BusinessRequestDTO.VisitBenefitCreateRequest request
    ) {
        return ApiResponse.onSuccess(
                ownerFacilityBenefitCommandService.createBenefit(userId, facilityId, request)
        );
    }

    /**
     * 방문 혜택 삭제. 노출 끄기(on/off 토글)와 달리 되돌릴 수 없는 하드 삭제다.
     */
    @DeleteMapping("/facilities/{facilityId}/benefits/{benefitId}")
    public ApiResponse<BusinessResponseDTO.VisitBenefitDeleteResult> deleteBenefit(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long facilityId,
            @PathVariable Long benefitId
    ) {
        return ApiResponse.onSuccess(
                ownerFacilityBenefitCommandService.deleteBenefit(userId, facilityId, benefitId)
        );
    }

    /**
     * 방문 혜택 노출 on/off. 끄면 손님에게 안 보이지만 삭제되지는 않는다(계절 혜택 재사용).
     */
    @PatchMapping("/facilities/{facilityId}/benefits/{benefitId}/enabled")
    public ApiResponse<BusinessResponseDTO.VisitBenefit> updateBenefitEnabled(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long facilityId,
            @PathVariable Long benefitId,
            @Valid @RequestBody BusinessRequestDTO.VisitBenefitEnabledUpdateRequest request
    ) {
        return ApiResponse.onSuccess(
                ownerFacilityBenefitCommandService.updateEnabled(userId, facilityId, benefitId, request)
        );
    }
}
