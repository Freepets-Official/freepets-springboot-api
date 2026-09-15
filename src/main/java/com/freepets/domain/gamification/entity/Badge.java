package com.freepets.domain.gamification.entity;

/**
 * 행동 기반 배지 카탈로그. 레벨 배지({@link LevelTier})와 별개로, 특정 행동을 달성하면 준다.
 *
 * <p>도메인별로 1회·중간·상위 단계로 나뉜 도전과제형 배지들이다 — 전체 카탈로그는 여전히
 * 콘텐츠 기획에 따라 계속 늘어날 수 있고(값 추가는 하위 호환 — {@link CourseTheme}과 같은 확장
 * 방식), 새 도메인이 생기면 그때 또 채워두면 된다. {@code relatedSourceType}은 {@link com.freepets.domain.gamification.service.BadgeEvaluationService#evaluateAfterXpEvent}가
 * "이 타입의 XpEvent가 막 하나 생겼을 때만 이 배지를 재평가하면 된다"는 걸 판단하는 용도고,
 * {@code threshold}는 그 타입의 누적 XpEvent 개수가 몇 번째에 달성되는지다 — "첫 판별"류는 1,
 * "리뷰 10개"는 10처럼, 조건을 분기문 없이 이 표에서 바로 읽을 수 있게 한다.
 *
 * <p>{@code HELPFUL_10}처럼 {@code relatedSourceType}이 {@code null}인 배지는 이 XpEvent 기반
 * 평가 대상이 아니다 — 본인 행동이 아니라 남이 눌러주는 게 트리거라 별도 평가 경로
 * ({@link com.freepets.domain.gamification.service.BadgeEvaluationService#evaluateHelpfulSaviorBadge})를 탄다.
 */
public enum Badge {

    /** 판별을 처음 이용. */
    FIRST_PETCHECK("첫 판별", "판별 기능을 처음 이용했어요", XpSourceType.PETCHECK, 1),

    /** 판별 10회. */
    PETCHECKS_10("판별 10회 달성", "판별 기능을 10번 이용했어요", XpSourceType.PETCHECK, 10),

    /** 판별 50회. */
    PETCHECKS_50("판별왕", "판별 기능을 50번 이용했어요", XpSourceType.PETCHECK, 50),

    /** 판별 100회. */
    PETCHECKS_100("전설의 판별사", "판별 기능을 100번 이용했어요", XpSourceType.PETCHECK, 100),

    /** 리뷰를 처음 작성. */
    FIRST_REVIEW("첫 리뷰", "첫 리뷰를 남겼어요", XpSourceType.REVIEW, 1),

    /** 리뷰 10개 작성. */
    REVIEWS_10("리뷰 10개 작성", "리뷰를 10개 작성했어요", XpSourceType.REVIEW, 10),

    /** 리뷰 50개 작성. */
    REVIEWS_50("리뷰왕", "리뷰를 50개 작성했어요", XpSourceType.REVIEW, 50),

    /** 리뷰 100개 작성. */
    REVIEWS_100("전설의 리뷰어", "리뷰를 100개 작성했어요", XpSourceType.REVIEW, 100),

    /** 거부 제보를 처음 남김. */
    FIRST_REPORT("첫 제보", "거부 제보를 처음 남겼어요", XpSourceType.REPORT, 1),

    /** 거부 제보 10회. */
    REPORTS_10("제보왕", "거부 제보를 10번 남겼어요", XpSourceType.REPORT, 10),

    /** 거부 제보 50회. */
    REPORTS_50("동네 파수꾼", "거부 제보를 50번 남겼어요", XpSourceType.REPORT, 50),

    /** 만족도를 처음 남김. */
    FIRST_SATISFACTION("첫 만족도 평가", "만족도를 처음 남겼어요", XpSourceType.SATISFACTION, 1),

    /** 만족도 10회. */
    SATISFACTIONS_10("기록왕", "만족도를 10번 남겼어요", XpSourceType.SATISFACTION, 10),

    /** 만족도 50회. */
    SATISFACTIONS_50("우리 아이 다이어리", "만족도를 50번 남겼어요", XpSourceType.SATISFACTION, 50),

    /** 코스를 처음 공개. */
    FIRST_COURSE_PUBLISHED("첫 공개 코스", "코스를 처음 공개했어요", XpSourceType.COURSE_PUBLISHED, 1),

    /** 코스 5개 공개. */
    COURSES_PUBLISHED_5("코스 크리에이터", "코스를 5개 공개했어요", XpSourceType.COURSE_PUBLISHED, 5),

    /** 코스 20개 공개. */
    COURSES_PUBLISHED_20("코스왕", "코스를 20개 공개했어요", XpSourceType.COURSE_PUBLISHED, 20),

    /** 공유한 코스가 5번 복사됨. */
    COURSE_SHARED_5("인기 코스 메이커", "내가 공유한 코스가 5번 복사됐어요", XpSourceType.COURSE_SHARED_COPY, 5),

    /** 공유한 코스가 20번 복사됨. */
    COURSE_SHARED_20("인기 코스왕", "내가 공유한 코스가 20번 복사됐어요", XpSourceType.COURSE_SHARED_COPY, 20),

    /** 공유한 코스가 50번 복사됨. */
    COURSE_SHARED_50("전설의 코스 크리에이터", "내가 공유한 코스가 50번 복사됐어요", XpSourceType.COURSE_SHARED_COPY, 50),

    /** 내가 쓴 리뷰들이 "도움됐어요"를 총 10번 받음(여러 리뷰에 걸쳐 합산). */
    HELPFUL_10("구원자", "내 리뷰가 다른 집사들에게 도움됐어요를 총 10번 받았어요", null, 10),

    /** 도움됐어요 총 50번. */
    HELPFUL_50("동네 구원자", "내 리뷰가 다른 집사들에게 도움됐어요를 총 50번 받았어요", null, 50),

    /** 도움됐어요 총 100번. */
    HELPFUL_100("전설의 구원자", "내 리뷰가 다른 집사들에게 도움됐어요를 총 100번 받았어요", null, 100);

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
