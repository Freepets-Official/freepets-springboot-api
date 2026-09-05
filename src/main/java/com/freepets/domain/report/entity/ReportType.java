package com.freepets.domain.report.entity;

public enum ReportType {
    INFO_CORRECTION,
    PET_POLICY_CHANGE,
    PERMANENTLY_CLOSED,
    NEW_FACILITY,
    ETC,

    // 문 앞에서 원터치로 보내는 실시간 거부 제보(F4). 항상 isRealtime=true와 함께 쓰인다.
    DENIED
}
