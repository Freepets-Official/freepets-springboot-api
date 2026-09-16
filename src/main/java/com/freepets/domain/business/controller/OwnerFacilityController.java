package com.freepets.domain.business.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.service.OwnerFacilityQueryService;
import com.freepets.global.apiPayload.ApiResponse;

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
}
