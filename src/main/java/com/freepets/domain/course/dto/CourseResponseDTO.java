package com.freepets.domain.course.dto;

import java.util.List;

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

    // GET /api/v1/courses/similar 응답.
    public record SimilarCourseResult(
            String title,
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

}
