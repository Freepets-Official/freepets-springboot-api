package com.freepets.domain.gamification.entity;

/**
 * 경험치를 준 행동의 종류. 도메인별 하루 지급 상한·평생 1회 여부 판단(GamificationService)과
 * 배지 조건 평가(BadgeEvaluationService)가 이 값으로 XpEvent를 구분해서 센다.
 */
public enum XpSourceType {

    /** 그룹 판별 요청(PetCheck) — 요청마다 지급. */
    PETCHECK,

    /** 리뷰 신규 작성(수정은 제외) — 시설당 리뷰가 1개라 하루 상한 없이 자연히 제한된다. */
    REVIEW,

    /** 거부 제보(F4) 제출 — 제출 즉시 반영되므로 제출 시점에 바로 지급. */
    REPORT,

    /** 만족도 신규 평가(같은 시설·같은 아이 재평가/수정은 제외). */
    SATISFACTION,

    /** 코스가 처음 공개(isPublic=true)로 전환된 시점 — 코스당 평생 1회. */
    COURSE_PUBLISHED,

    /** 내가 공유한 코스를 다른 사람이 공유 코드로 복사해간 시점 — 원본 소유자에게 지급. */
    COURSE_SHARED_COPY

}
