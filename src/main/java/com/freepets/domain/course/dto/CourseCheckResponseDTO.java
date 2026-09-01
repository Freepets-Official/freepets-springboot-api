package com.freepets.domain.course.dto;

import java.util.List;

import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.petcheck.entity.PetCheckResult;

public class CourseCheckResponseDTO {

    private CourseCheckResponseDTO() {}

    // POST /api/v1/ai/course-check 응답.
    public record CourseCheckResult(
            PetCheckResult overall,

            /** overall이 DENIED인 스톱 수. */
            long blockedCount,
            List<Stop> stops
    ) {}

    public record Stop(
            FacilitySummary facility,

            /** 첫 스톱 10:00, 스톱당 +90분(데모 고정, 08-course-check.md). "HH:mm". */
            String time,
            List<StopVerdict> verdicts,
            PetCheckResult overall,

            /**
             * overall이 DENIED인 스톱에만 채워진다(같은 카테고리·코스에 없음·그룹 전체 통과 조건을
             * 만족하는 가장 가까운 1곳). 조건에 맞는 곳을 못 찾으면 {@code null} — 프론트가 "같은
             * 성격의 대체 시설을 찾지 못했어요. 이 스톱은 빼는 것을 권장해요"로 안내한다.
             * CONDITIONAL은 대안 없이 통과 처리.
             */
            Alternative alternative
    ) {}

    public record FacilitySummary(
            Long facilityId,
            String name,
            FacilityCategory category
    ) {}

    public record StopVerdict(
            Long petId,
            String petName,
            PetCheckResult result,
            String reason,
            List<String> conditions
    ) {}

    public record Alternative(
            Long facilityId,
            String name,
            double distanceKm
    ) {}

}
