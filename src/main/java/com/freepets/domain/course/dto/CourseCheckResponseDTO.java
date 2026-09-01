package com.freepets.domain.course.dto;

import java.util.List;

import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.petcheck.entity.PetCheckResult;

public class CourseCheckResponseDTO {

    private CourseCheckResponseDTO() {}

    // POST /api/v1/ai/course-check 응답.
    public record CourseCheckResult(
            PetCheckResult overall,
            List<Stop> stops
    ) {}

    public record Stop(
            FacilitySummary facility,

            /** 첫 스톱 10:00, 스톱당 +90분(데모 고정, 08-course-check.md). "HH:mm". */
            String time,
            List<StopVerdict> verdicts,
            PetCheckResult overall,

            /** overall이 DENIED인 스톱에만 채워진다. CONDITIONAL은 대안 없이 통과 처리. */
            List<Alternative> alternatives
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
