package com.freepets.domain.facility.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.facility.dto.FacilityRequestDTO;
import com.freepets.domain.facility.dto.FacilityResponseDTO;
import com.freepets.domain.facility.service.FacilityListQueryService;
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
    private final FacilityListQueryService facilityListQueryService;

    /**
     * 조건에 맞는 전체 시설 목록을 이름순으로 조회한다.
     *
     * <p>지역을 지정하면 관광공사에서 요청마다 실시간으로 받아오고, 우리 DB는 발자국 점수·리뷰
     * 수·동반 가능 여부를 붙이는 데 쓴다({@code FacilityListQueryService} 참고).
     *
     * <p>지역을 지정할 때는 시군구까지 받는다. 관광공사에서 조건에 맞는 전량을 한 번에 받아오는
     * 구조라, 시도만 받으면 한 요청에 9천 건이 넘게 딸려 온다. 하위 시군구 행이 없는 시도만
     * 시군구를 비울 수 있는데, 세종특별자치시도 시군구 코드가 시도와 같은 {@code 36110}으로
     * 내려와 지금 데이터에서는 예외가 없다.
     *
     * <p>지역을 생략하면 전국이며, 이때는 관광공사 대신 적재해둔 DB에서 내려간다. 전국은 5만 건에
     * 가까워 한 응답으로 받을 수 없기 때문이다. 프론트가 전국 합계를 내려고 시군구를 모두 순회하면
     * 화면 한 번에 관광공사 호출이 그 수만큼 나가므로, 그 경로를 막기 위한 것이기도 하다.
     *
     * <p>좌표를 받지 않는다. 거리순이 아니라 이름순이라 필요가 없고, 덕분에 개인위치정보가
     * 액세스 로그에 남지 않아 GET으로 받아도 된다.
     *
     * <p>게스트(토큰 없음)도 호출할 수 있다. 개인화 없이 공개 데이터만 내려간다.
     */
    @GetMapping
    public ApiResponse<FacilityResponseDTO.FacilityListResult> getFacilityList(
            @Valid @ModelAttribute FacilityRequestDTO.FacilityListRequest request
    ) {
        return ApiResponse.onSuccess(
                facilityListQueryService.getFacilityList(request)
        );
    }

    /**
     * 조건에 맞는 시설 목록을 거리순으로 조회한다.
     *
     * <p>조회지만 POST로 받는다. 위도·경도는 개인위치정보라 쿼리 스트링에 실으면
     * 웹 서버 액세스 로그와 APM 트레이스에 그대로 쌓인다. GET의 이점인 캐싱은
     * 좌표마다 응답이 달라 얻을 것이 없다.
     *
     * <p>게스트(토큰 없음)도 호출할 수 있다. 개인화 없이 공개 데이터만 내려가는
     * 순수 조회라 로그인 여부와 무관하다({@code SecurityConfig} 참고).
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
     * 발자국 등급 랭킹을 조회한다.
     *
     * <p>등급을 받은 시설만 등급 → 친화도 점수 순으로 내려간다. 지역·거리·카테고리·동반 여부
     * 네 필터는 모두 선택이며 AND로 겹친다.
     *
     * <p>목록 검색과 달리 GET이다. 필터 조합이 곧 화면 상태라 링크로 공유될 수 있고, 좌표 없이도
     * 조회되어야 하기 때문이다. 대신 좌표를 함께 보내면 상세 조회와 마찬가지로 개인위치정보가
     * 액세스 로그와 APM 트레이스에 남으므로, 웹 서버 쪽에 파라미터 마스킹을 걸어둬야 한다.
     */
    @GetMapping("/ranking")
    public ApiResponse<FacilityResponseDTO.RankingResult> getFacilityRanking(
            @Valid @ModelAttribute FacilityRequestDTO.RankingRequest request
    ) {
        return ApiResponse.onSuccess(
                facilityQueryService.getRanking(request)
        );
    }

    /**
     * 랭킹 화면의 지역 칩 목록을 조회한다.
     *
     * <p>발자국 등급을 받은 시설이 있는 지역만 내려간다. 프론트가 지역 상수를 들고 있지 않는 이유는,
     * 관광공사 동기화로 시설이 계속 늘어 하드코딩한 목록이 실제 데이터와 어긋나기 때문이다.
     *
     * <p>{@code /{facilityId}}보다 먼저 선언할 필요는 없다. 경로 변수가 {@code Long}이라
     * {@code regions}는 애초에 그쪽으로 매칭되지 않는다.
     */
    @GetMapping("/regions")
    public ApiResponse<List<FacilityResponseDTO.Region>> getFacilityRegions() {
        return ApiResponse.onSuccess(
                facilityQueryService.getRegions()
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
     *
     * <p>게스트(토큰 없음)도 호출할 수 있다. 이때 {@code userId}는 null로 들어오며, 반려동물
     * 궁합 같은 개인화만 빠지고 나머지 공개 정보는 그대로 내려간다.
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
