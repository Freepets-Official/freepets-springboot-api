package com.freepets.domain.facility.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.facility.dto.FacilityRequestDTO;
import com.freepets.domain.facility.dto.FacilityResponseDTO;
import com.freepets.domain.facility.service.FacilityQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/facilities")
@RequiredArgsConstructor
// 쿼리 파라미터에 건 제약을 동작시키려면 클래스 단위 선언이 필요하다. 위반은
// ConstraintViolationException으로 올라가 GlobalExceptionHandler가 400으로 바꾼다.
@Validated
public class FacilityController {

    private final FacilityQueryService facilityQueryService;

    /**
     * 조건에 맞는 시설 목록을 거리순으로 조회한다.
     *
     * <p>조회지만 POST로 받는다. 위도·경도는 개인위치정보라 쿼리 스트링에 실으면
     * 웹 서버 액세스 로그와 APM 트레이스에 그대로 쌓인다. GET의 이점인 캐싱은
     * 인증이 필요한 데다 좌표마다 응답이 달라 얻을 것이 없다.
     */
    @PostMapping("/search")
    public ApiResponse<FacilityResponseDTO.FacilitySearchResult> searchFacilities(
            @Valid @RequestBody FacilityRequestDTO.SearchRequest request
    ) {
        return ApiResponse.onSuccess(
                facilityQueryService.searchFacilities(request)
        );
    }

    /**
     * 시설 하나의 상세 정보를 조회한다.
     *
     * <p>목록과 달리 GET으로 받는다. 상세는 링크로 공유·북마크되는 자원이고, 좌표를 안 보내도
     * 나머지 정보는 그대로 내려가야 하기 때문이다. 대신 좌표가 액세스 로그와 APM 트레이스에
     * 남으므로 웹 서버 쪽에 파라미터 마스킹을 걸어둬야 한다.
     *
     * <p>좌표는 선택이다. 위치 권한을 거부했거나 딥링크로 바로 들어온 경우 거리를 낼 수 없으므로
     * {@code distanceM}만 비워서 내려준다. 다만 둘 중 하나만 보내는 것은 실수이므로 400으로 막는다.
     */
    @GetMapping("/{facilityId}")
    public ApiResponse<FacilityResponseDTO.FacilityDetail> getFacilityDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable("facilityId") Long facilityId,
            @RequestParam(name = "latitude", required = false)
            @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
            @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
            Double latitude,
            @RequestParam(name = "longitude", required = false)
            @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
            @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
            Double longitude
    ) {
        return ApiResponse.onSuccess(
                facilityQueryService.getFacilityDetail(facilityId, userId, latitude, longitude)
        );
    }
}
