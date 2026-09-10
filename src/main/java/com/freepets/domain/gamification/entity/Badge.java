package com.freepets.domain.gamification.entity;

/**
 * 행동 기반 배지 카탈로그. 레벨 배지({@link LevelTier})와 별개로, 특정 행동을 달성하면 준다.
 *
 * <p>1차 시작 세트 5개 — 전체 카탈로그는 콘텐츠 기획이 필요한 별도 작업이라 여기서는 몇 개만
 * 채워두고, 나중에 값을 더 추가하면 된다(값 추가는 하위 호환 — {@link CourseTheme}과 같은 확장
 * 방식). {@code relatedSourceType}은 {@link com.freepets.domain.gamification.service.BadgeEvaluationService}가
 * "이 타입의 XpEvent가 막 하나 생겼을 때만 이 배지를 재평가하면 된다"는 걸 판단하는 용도고,
 * {@code threshold}는 그 타입의 누적 XpEvent 개수가 몇 번째에 달성되는지다 — "첫 판별"류는 1,
 * "리뷰 10개"는 10처럼, 조건을 분기문 없이 이 표에서 바로 읽을 수 있게 한다.
 */
public enum Badge {

    /** 판별을 처음 이용. */
    FIRST_PETCHECK("첫 판별", "판별 기능을 처음 이용했어요", XpSourceType.PETCHECK, 1),

    /** 리뷰를 처음 작성. */
    FIRST_REVIEW("첫 리뷰", "첫 리뷰를 남겼어요", XpSourceType.REVIEW, 1),

    /** 리뷰 10개 작성. */
    REVIEWS_10("리뷰 10개 작성", "리뷰를 10개 작성했어요", XpSourceType.REVIEW, 10),

    /** 코스를 처음 공개. */
    FIRST_COURSE_PUBLISHED("첫 공개 코스", "코스를 처음 공개했어요", XpSourceType.COURSE_PUBLISHED, 1),

    /** 공유한 코스가 5번 복사됨. */
    COURSE_SHARED_5("인기 코스 메이커", "내가 공유한 코스가 5번 복사됐어요", XpSourceType.COURSE_SHARED_COPY, 5);

    private final String label;
    private final String description;
    private final XpSourceType relatedSourceType;
    private final int threshold;

    Badge(
            String label,
            String description,
            XpSourceType relatedSourceType,
            int threshold
    ) {
        this.label = label;
        this.description = description;
        this.relatedSourceType = relatedSourceType;
        this.threshold = threshold;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public XpSourceType getRelatedSourceType() {
        return relatedSourceType;
    }

    public int getThreshold() {
        return threshold;
    }

}
