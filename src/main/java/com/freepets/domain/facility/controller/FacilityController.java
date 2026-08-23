package com.freepets.domain.facility.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.facility.dto.FacilityRequestDTO;
import com.freepets.domain.facility.dto.FacilityResponseDTO;
import com.freepets.domain.facility.service.FacilityQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/facilities")
@RequiredArgsConstructor
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
}
