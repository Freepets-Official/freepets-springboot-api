package com.freepets.domain.gamification.entity;

/**
 * {@link Badge}를 "무엇을 세는가"로 묶은 단위. 배지 하나하나(예: {@code REVIEW_GOLD})가 아니라
 * 패밀리(예: 리뷰) 단위로 "지금 몇 회 했고 다음 단계까지 얼마나 남았는지"를 보여주는 게
 * {@code GET /me/gamification}의 {@code progress[]}다 — 퀘스트 진행바를 그리려면 획득한
 * 배지만으론 부족하고 이 누적치가 필요하다.
 *
 * <p>{@code relatedSourceType}이 있으면 {@link com.freepets.domain.gamification.repository.XpEventRepository}로
 * 누적 횟수를 센다. {@code null}인 건({@code HELPFUL}) XpEvent가 안 생기는 행동이라(남이 눌러주는
 * "도움됐어요") 리뷰 도메인이 들고 있는 합계를 대신 쓴다({@link com.freepets.domain.gamification.service.GamificationQueryService}
 * 참고).
 */
public enum BadgeFamily {

    PETCHECK("판별", XpSourceType.PETCHECK),
    REVIEW("리뷰", XpSourceType.REVIEW),
    REPORT("제보", XpSourceType.REPORT),
    SATISFACTION("만족도", XpSourceType.SATISFACTION),
    COURSE_PUBLISHED("공개 코스", XpSourceType.COURSE_PUBLISHED),
    COURSE_SHARED("인기 코스", XpSourceType.COURSE_SHARED_COPY),
    HELPFUL("구원자", null);

    private final String label;
    private final XpSourceType relatedSourceType;

    BadgeFamily(
            String label,
            XpSourceType relatedSourceType
    ) {
        this.label = label;
        this.relatedSourceType = relatedSourceType;
    }

    public String getLabel() {
        return label;
    }

    public XpSourceType getRelatedSourceType() {
        return relatedSourceType;
    }

}
