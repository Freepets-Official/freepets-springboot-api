package com.freepets.domain.course.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.freepets.domain.course.entity.CourseDistanceOption;
import com.freepets.domain.course.entity.CourseTheme;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.review.entity.Tag;

public class CourseResponseDTO {

    private CourseResponseDTO() {}

    // GET /api/v1/courses/liked 응답.
    public record LikedCourseResult(
            String title,
            List<LikedStop> stops
    ) {}

    public record LikedStop(
            Long facilityId,
            String name,
            FacilityCategory category,
            double avgSatisfaction,
            List<ReasonPet> reasonPets
    ) {}

    // 요청으로 넘어온 petIds 중 이 시설에 만족도 기록이 있는 펫만 담는다 — 필터링(avgSatisfaction)과
    // 무관한 표시 전용.
    public record ReasonPet(
            Long petId,
            String petName,
            float score
    ) {}

    // GET /api/v1/courses/similar 응답. isPersonalized=false는 취향 프로필(만족도 기록)이
    // 아직 없는 신규 유저에게 리뷰 평점 기준 대체 추천을 보여준 것 — 프론트가 "취향 기반"인
    // 척 하지 않도록 구분해서 내려준다.
    public record SimilarCourseResult(
            String title,
            boolean isPersonalized,
            List<SimilarStop> stops
    ) {}

    public record SimilarStop(
            Long facilityId,
            String name,
            List<Tag> matchedTags,
            boolean matchedByKind,
            boolean matchedByBreedSize,
            String reason
    ) {}

    // GET /api/v1/courses/themes 응답 — preset의 theme 드롭다운용. CourseTheme은 DB가 아니라
    // 코드에 고정된 값이라 서버가 어떤 값·라벨을 쓰는지 프론트가 하드코딩하지 않게 내려준다.
    public record ThemeList(
            List<ThemeOption> themes
    ) {}

    public record ThemeOption(
            CourseTheme value,
            String label
    ) {}

    // GET /api/v1/courses/regions 응답 — preset의 sido/sigungu 드롭다운용.
    public record RegionList(
            List<SidoRegion> sidos
    ) {}

    // GET /api/v1/courses/distance-options 응답 — 거리 슬라이더 선택지 목록.
    public record DistanceOptionList(
            List<DistanceOption> options
    ) {}

    public record DistanceOption(
            CourseDistanceOption value,
            String label,
            int meters
    ) {}

    public record SidoRegion(
            String sido,

            /** 시/도 전체를 아우르는 시설만 있으면 빈 배열 — 이때 sigungu 없이 sido만으로 조회한다. */
            List<String> sigungus
    ) {}

    // GET /api/v1/courses/preset 응답.
    public record PresetCourseResult(
            Long courseId,
            String title,
            List<PresetStop> stops
    ) {}

    public record PresetStop(
            Long facilityId,
            String name,
            FacilityCategory category,
            double score,

            /**
             * 코스 첫 스톱으로부터의 거리(m). GPS/지역 좌표 같은 별도 기준점 입력이 없어, 이
             * 코스 동선의 시작점을 기준점으로 삼는다 — 첫 스톱 자신은 항상 0.
             */
            double distanceM
    ) {}

    // GET /api/v1/courses(내 코스), POST/PUT 응답 — CUSTOM 코스.
    public record MyCourse(
            Long courseId,
            String name,
            String description,
            List<Long> stopIds,
            LocalDateTime createdAt,

            // record 접근자가 isPublic()이므로 JSON 프로퍼티명을 ApiResponse.isSuccess와 같은
            // 이유로 isPublic으로 명시 고정(Jackson 기본값은 public으로 깎는다).
            @JsonProperty("isPublic")
            boolean isPublic
    ) {}

    // DELETE /api/v1/courses/{courseId} 응답.
    public record DeleteResult(
            Long courseId
    ) {}

    // GET /api/v1/courses/public 응답 — 다른 사용자가 공개한 CUSTOM 코스 둘러보기(트리플의
    // "다른 여행자 코스" 참고). 그대로 stopIds를 담아 POST /courses에 넣으면 내 코스로 복사(fork)된다.
    public record PublicCourseResult(
            List<PublicCourse> items,
            long total
    ) {}

    public record PublicCourse(
            Long courseId,
            String name,
            String description,
            String ownerNickname,
            List<Long> stopIds,
            LocalDateTime createdAt
    ) {}

    // POST /api/v1/courses/optimize-order 응답. 저장하지 않고 순서만 다듬어 미리 보여준다 —
    // 그대로 쓰려면 이어서 이 stopIds를 POST/PUT /courses에 넣어 호출해야 한다.
    public record OrderResult(
            List<Long> stopIds
    ) {}

}
