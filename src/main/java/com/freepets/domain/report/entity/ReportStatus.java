package com.freepets.domain.report.entity;

public enum ReportStatus {
    PENDING,
    APPROVED,
    REJECTED,

    // 실시간 거부 제보(F4)는 검토를 기다리지 않고 접수 즉시 반영된다.
    APPLIED
}
