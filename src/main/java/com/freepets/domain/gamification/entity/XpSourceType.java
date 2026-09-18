package com.freepets.domain.gamification.entity;

/**
 * 경험치를 준 행동의 종류. 도메인별 하루 지급 상한·평생 1회 여부 판단(GamificationService)과
 * 배지 조건 평가(BadgeEvaluationService)가 이 값으로 XpEvent를 구분해서 센다.
 *
 * <p>{@code label}·{@code dailyCap}은 GamificationService(지급 시 상한 체크)와
 * GamificationQueryService(오늘의 퀘스트 조회, {@code GET /me/gamification/quests})가 함께
 * 참조한다 — 상한 숫자가 두 곳에 따로 있으면 하나만 고치는 사고가 나므로 여기 한 곳에만 둔다.
 */
public enum XpSourceType {

    /** 그룹 판별 요청(PetCheck) — 요청마다 지급. */
    PETCHECK("판별하기", 10),

    /** 리뷰 신규 작성(수정은 제외). 같은 시설엔 평생 1개라 자연히 제한되지만, 서로 다른 시설을
     *  돌면 하루에 여러 번 지급될 수 있어 다른 도메인처럼 하루 상한을 둔다. */
    REVIEW("리뷰 남기기", 5),

    /** 거부 제보(F4) 제출 — 제출 즉시 반영되므로 제출 시점에 바로 지급. */
    REPORT("거부 제보하기", 5),

    /** 만족도 신규 평가(같은 시설·같은 아이 재평가/수정은 제외). */
    SATISFACTION("만족도 평가하기", 5),

    /** 코스가 처음 공개(isPublic=true)로 전환된 시점 — 코스당 평생 1회. 같은 시설을 스톱으로
     *  재사용하는 트리비얼한 코스를 무한정 새로 만들어 공개하면 하루 상한 없이 XP를 무제한으로
     *  쌓을 수 있어(평생 1회는 courseId 단위 검사라 매번 새 courseId면 통과) 하루 상한도 둔다. */
    COURSE_PUBLISHED("코스 공개하기", 5),

    /** 내가 공유한 코스를 다른 사람이 공유 코드로 복사해간 시점 — 원본 소유자에게 지급. */
    COURSE_SHARED_COPY("코스가 공유되기", 10);

    private final String label;
    private final int dailyCap;

    XpSourceType(
            String label,
            int dailyCap
    ) {
        this.label = label;
        this.dailyCap = dailyCap;
    }

    public String getLabel() {
        return label;
    }

    public int getDailyCap() {
        return dailyCap;
    }

}
