package com.freepets.domain.report.repository;

/** 시설별 거부 제보 수 집계 결과. 제보가 없는 시설은 결과에 나타나지 않으니 호출부에서 0으로 채운다. */
public record FacilityDenialReportCount(
        Long facilityId,
        long reportCount
) {}
