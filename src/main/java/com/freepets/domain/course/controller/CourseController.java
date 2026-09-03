package com.freepets.domain.course.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.course.dto.CourseRequestDTO;
import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.CourseDistanceOption;
import com.freepets.domain.course.entity.CourseTheme;
import com.freepets.domain.course.service.CourseCommandService;
import com.freepets.domain.course.service.CourseLikedService;
import com.freepets.domain.course.service.CoursePresetService;
import com.freepets.domain.course.service.CourseQueryService;
import com.freepets.domain.course.service.CourseSimilarService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
// petIds 같은 쿼리 파라미터 제약을 동작시키려면 클래스 단위 선언이 필요하다(FacilityController와
// 같은 이유) — 위반은 ConstraintViolationException으로 올라가 GlobalExceptionHandler가 400으로 바꾼다.
@Validated
public class CourseController {

    private final CourseLikedService courseLikedService;
    private final CourseSimilarService courseSimilarService;
    private final CoursePresetService coursePresetService;
    private final CourseQueryService courseQueryService;
    private final CourseCommandService courseCommandService;

    @GetMapping
    public ApiResponse<List<CourseResponseDTO.MyCourse>> getMyCourses(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.onSuccess(
                courseQueryService.getMyCourses(userId)
        );
    }

    @PostMapping
    public ApiResponse<CourseResponseDTO.MyCourse> createCourse(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CourseRequestDTO.SaveRequest request
    ) {
        return ApiResponse.onSuccess(
                courseCommandService.createCourse(userId, request)
        );
    }

    @PutMapping("/{courseId}")
    public ApiResponse<CourseResponseDTO.MyCourse> updateCourse(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long courseId,
            @Valid @RequestBody CourseRequestDTO.SaveRequest request
    ) {
        return ApiResponse.onSuccess(
                courseCommandService.updateCourse(userId, courseId, request)
        );
    }

    /**
     * 그 자리(0부터 시작하는 순서)의 스톱만 다른 시설로 교체한다 — 예: 스톱이 5개(순서 0~4)일 때
     * stopOrder=3에 새 facilityId를 보내면 그 자리만 바뀌고 나머지 순서는 그대로 유지된다.
     * 스톱 개수 자체가 바뀌는 추가·삭제는 여전히 updateCourse(전체 stopIds 교체)를 쓴다.
     */
    @PutMapping("/{courseId}/stops/{stopOrder}")
    public ApiResponse<CourseResponseDTO.MyCourse> replaceStop(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long courseId,
            @PathVariable int stopOrder,
            @Valid @RequestBody CourseRequestDTO.ReplaceStopRequest request
    ) {
        return ApiResponse.onSuccess(
                courseCommandService.replaceStop(userId, courseId, stopOrder, request.getFacilityId())
        );
    }

    @DeleteMapping("/{courseId}")
    public ApiResponse<CourseResponseDTO.DeleteResult> deleteCourse(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long courseId
    ) {
        return ApiResponse.onSuccess(
                courseCommandService.deleteCourse(userId, courseId)
        );
    }

    /**
     * 저장하지 않고 스톱 순서만 최근접 이웃 방식으로 다듬어 미리 보여준다("경로 최적화").
     * AI 코스를 fork했거나 직접 검색해서 스톱을 추가한 뒤 동선이 왔다갔다 하면 이 결과를 그대로
     * POST/PUT의 stopIds로 넣어 저장하면 된다.
     */
    @PostMapping("/optimize-order")
    public ApiResponse<CourseResponseDTO.OrderResult> optimizeOrder(
            @Valid @RequestBody CourseRequestDTO.OptimizeOrderRequest request
    ) {
        return ApiResponse.onSuccess(
                courseCommandService.optimizeOrder(request.getStopIds())
        );
    }

    // 로그인 불필요 — 다른 사용자가 공개한 코스를 로그인 전에도 둘러보고 담아갈 마음이 들게 한다.
    @GetMapping("/public")
    public ApiResponse<CourseResponseDTO.PublicCourseResult> getPublicCourses(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
            int page,
            @RequestParam(defaultValue = "15")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
            int size
    ) {
        return ApiResponse.onSuccess(
                courseQueryService.getPublicCourses(PageRequest.of(page, size))
        );
    }

    // 로그인 불필요 — preset과 마찬가지로 로그인 전 지역 선택 드롭다운에 쓴다.
    @GetMapping("/regions")
    public ApiResponse<CourseResponseDTO.RegionList> getRegions() {
        return ApiResponse.onSuccess(
                coursePresetService.getRegions()
        );
    }

    // 로그인 불필요 — regions와 같은 이유로, 테마 선택 드롭다운에 쓴다.
    @GetMapping("/themes")
    public ApiResponse<CourseResponseDTO.ThemeList> getThemes() {
        return ApiResponse.onSuccess(
                coursePresetService.getThemes()
        );
    }

    // 로그인 불필요 — regions/themes와 같은 이유로, 거리 슬라이더에 쓴다.
    @GetMapping("/distance-options")
    public ApiResponse<CourseResponseDTO.DistanceOptionList> getDistanceOptions() {
        return ApiResponse.onSuccess(
                coursePresetService.getDistanceOptions()
        );
    }

    // 로그인 불필요 — regions/themes와 함께 로그인 전에도 쓸 수 있는 코스 모드(07-courses.md 참고).
    //
    // sido/sigungu는 DB(Facility.sido/sigungu)에 실제로 있는 값과 비교되는 자유 텍스트라 엄밀히는
    // 고정 이넘이 아니지만(운영 배치가 새 시/도를 시딩하면 서버 재배포 없이 즉시 유효해야 함),
    // 대한민국 17개 시/도는 사실상 바뀌지 않으므로 sido만 Swagger에서 테스트하기 편하게
    // allowableValues로 드롭다운을 띄운다 — 서버가 이 목록을 강제하는 건 아니다(그래서 이넘이
    // 아니라 String + Schema 힌트). sigungu는 시/도별로 달라 고정 목록을 못 두니, 실제 값은
    // GET /courses/regions로 조회. theme은 CourseTheme 자체가 이넘이라 이 어노테이션 없이도
    // springdoc이 자동으로 드롭다운을 만들어준다.
    @GetMapping("/preset")
    public ApiResponse<CourseResponseDTO.PresetCourseResult> getPresetCourse(
            @Parameter(
                    description = "시/도. GET /courses/regions로 실제 동반 가능 시설이 있는 값만 확인 가능",
                    schema = @Schema(allowableValues = {
                            "서울특별시", "부산광역시", "대구광역시", "인천광역시", "광주광역시", "대전광역시",
                            "울산광역시", "세종특별자치시", "경기도", "강원특별자치도", "충청북도", "충청남도",
                            "전북특별자치도", "전라남도", "경상북도", "경상남도", "제주특별자치도"
                    })
            )
            @RequestParam @NotEmpty(message = "지역을 선택해주세요.") String sido,
            @Parameter(description = "시/군/구. 비우면 시/도 전체 대상 — 실제 값은 GET /courses/regions 응답 참고")
            @RequestParam(required = false) String sigungu,
            @Parameter(description = "코스 테마 — 라벨은 GET /courses/themes 참고")
            @RequestParam CourseTheme theme,
            @Parameter(description = "스톱 간 최대 거리 — 라벨은 GET /courses/distance-options 참고, 생략하면 5km")
            @RequestParam(required = false) CourseDistanceOption maxDistanceM
    ) {
        return ApiResponse.onSuccess(
                coursePresetService.getPreset(sido, sigungu, theme, maxDistanceM)
        );
    }

    @GetMapping("/liked")
    public ApiResponse<CourseResponseDTO.LikedCourseResult> getLikedCourse(
            @AuthenticationPrincipal Long userId,
            @RequestParam @NotEmpty(message = "반려동물을 1마리 이상 선택해주세요.") List<Long> petIds,
            @Parameter(description = "스톱 간 최대 거리 — 라벨은 GET /courses/distance-options 참고, 생략하면 5km")
            @RequestParam(required = false) CourseDistanceOption maxDistanceM,
            @Parameter(description = "시/도로 후보를 좁힌다 — 생략하면 전국 대상. 실제 값은 GET /courses/regions 참고")
            @RequestParam(required = false) String sido,
            @Parameter(description = "시/군/구로 후보를 좁힌다 — sido 없이는 무시된다")
            @RequestParam(required = false) String sigungu,
            @Parameter(description = "테마로 후보를 좁힌다 — 라벨은 GET /courses/themes 참고")
            @RequestParam(required = false) CourseTheme theme
    ) {
        return ApiResponse.onSuccess(
                courseLikedService.getLikedCourse(userId, petIds, maxDistanceM, sido, sigungu, theme)
        );
    }

    @GetMapping("/similar")
    public ApiResponse<CourseResponseDTO.SimilarCourseResult> getSimilarCourse(
            @AuthenticationPrincipal Long userId,
            @RequestParam @NotEmpty(message = "반려동물을 1마리 이상 선택해주세요.") List<Long> petIds,
            @Parameter(description = "스톱 간 최대 거리 — 라벨은 GET /courses/distance-options 참고, 생략하면 5km")
            @RequestParam(required = false) CourseDistanceOption maxDistanceM,
            @Parameter(description = "시/도로 후보를 좁힌다 — 생략하면 전국 대상. 실제 값은 GET /courses/regions 참고")
            @RequestParam(required = false) String sido,
            @Parameter(description = "시/군/구로 후보를 좁힌다 — sido 없이는 무시된다")
            @RequestParam(required = false) String sigungu,
            @Parameter(description = "테마로 후보를 좁힌다 — 라벨은 GET /courses/themes 참고")
            @RequestParam(required = false) CourseTheme theme
    ) {
        return ApiResponse.onSuccess(
                courseSimilarService.getSimilarCourse(userId, petIds, maxDistanceM, sido, sigungu, theme)
        );
    }

}
