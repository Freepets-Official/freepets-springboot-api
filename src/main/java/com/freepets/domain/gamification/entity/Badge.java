package com.freepets.domain.gamification.entity;

/**
 * 행동 기반 배지 카탈로그. 레벨 배지({@link LevelTier})와 별개로, 특정 행동을 달성하면 준다.
 *
 * <p>도메인마다 동일한 6단계(1·5·10·50·100·500회)로 나뉘고, 단계 이름도 동/은/금/루비/크리스탈/
 * 다이아로 모든 도메인에서 통일한다 — 도메인별로 다른 개수·다른 이름을 쓰던 이전 방식 대신, 어느
 * 도메인이든 "동=1회, 다이아=500회"라는 것만 기억하면 되게 한다. {@code relatedSourceType}은
 * {@link com.freepets.domain.gamification.service.BadgeEvaluationService#evaluateAfterXpEvent}가
 * "이 타입의 XpEvent가 막 하나 생겼을 때만 이 배지를 재평가하면 된다"는 걸 판단하는 용도고,
 * {@code threshold}는 그 타입의 누적 달성 횟수가 몇 번째에 달성되는지다.
 *
 * <p>{@code HELPFUL_*}처럼 {@code relatedSourceType}이 {@code null}인 배지는 이 XpEvent 기반
 * 평가 대상이 아니다 — 본인 행동이 아니라 남이 눌러주는 게 트리거라 별도 평가 경로
 * ({@link com.freepets.domain.gamification.service.BadgeEvaluationService#evaluateHelpfulSaviorBadge})를 탄다.
 */
public enum Badge {

    // 판별(PETCHECK)
    PETCHECK_BRONZE("판별 동", "판별 기능을 1번 이용했어요", XpSourceType.PETCHECK, 1),
    PETCHECK_SILVER("판별 은", "판별 기능을 5번 이용했어요", XpSourceType.PETCHECK, 5),
    PETCHECK_GOLD("판별 금", "판별 기능을 10번 이용했어요", XpSourceType.PETCHECK, 10),
    PETCHECK_RUBY("판별 루비", "판별 기능을 50번 이용했어요", XpSourceType.PETCHECK, 50),
    PETCHECK_CRYSTAL("판별 크리스탈", "판별 기능을 100번 이용했어요", XpSourceType.PETCHECK, 100),
    PETCHECK_DIAMOND("판별 다이아", "판별 기능을 500번 이용했어요", XpSourceType.PETCHECK, 500),

    // 리뷰(REVIEW)
    REVIEW_BRONZE("리뷰 동", "리뷰를 1개 작성했어요", XpSourceType.REVIEW, 1),
    REVIEW_SILVER("리뷰 은", "리뷰를 5개 작성했어요", XpSourceType.REVIEW, 5),
    REVIEW_GOLD("리뷰 금", "리뷰를 10개 작성했어요", XpSourceType.REVIEW, 10),
    REVIEW_RUBY("리뷰 루비", "리뷰를 50개 작성했어요", XpSourceType.REVIEW, 50),
    REVIEW_CRYSTAL("리뷰 크리스탈", "리뷰를 100개 작성했어요", XpSourceType.REVIEW, 100),
    REVIEW_DIAMOND("리뷰 다이아", "리뷰를 500개 작성했어요", XpSourceType.REVIEW, 500),

    // 제보(REPORT)
    REPORT_BRONZE("제보 동", "거부 제보를 1번 남겼어요", XpSourceType.REPORT, 1),
    REPORT_SILVER("제보 은", "거부 제보를 5번 남겼어요", XpSourceType.REPORT, 5),
    REPORT_GOLD("제보 금", "거부 제보를 10번 남겼어요", XpSourceType.REPORT, 10),
    REPORT_RUBY("제보 루비", "거부 제보를 50번 남겼어요", XpSourceType.REPORT, 50),
    REPORT_CRYSTAL("제보 크리스탈", "거부 제보를 100번 남겼어요", XpSourceType.REPORT, 100),
    REPORT_DIAMOND("제보 다이아", "거부 제보를 500번 남겼어요", XpSourceType.REPORT, 500),

    // 만족도(SATISFACTION)
    SATISFACTION_BRONZE("만족도 동", "만족도를 1번 남겼어요", XpSourceType.SATISFACTION, 1),
    SATISFACTION_SILVER("만족도 은", "만족도를 5번 남겼어요", XpSourceType.SATISFACTION, 5),
    SATISFACTION_GOLD("만족도 금", "만족도를 10번 남겼어요", XpSourceType.SATISFACTION, 10),
    SATISFACTION_RUBY("만족도 루비", "만족도를 50번 남겼어요", XpSourceType.SATISFACTION, 50),
    SATISFACTION_CRYSTAL("만족도 크리스탈", "만족도를 100번 남겼어요", XpSourceType.SATISFACTION, 100),
    SATISFACTION_DIAMOND("만족도 다이아", "만족도를 500번 남겼어요", XpSourceType.SATISFACTION, 500),

    // 코스 공개(COURSE_PUBLISHED)
    COURSE_PUBLISHED_BRONZE("공개 코스 동", "코스를 1개 공개했어요", XpSourceType.COURSE_PUBLISHED, 1),
    COURSE_PUBLISHED_SILVER("공개 코스 은", "코스를 5개 공개했어요", XpSourceType.COURSE_PUBLISHED, 5),
    COURSE_PUBLISHED_GOLD("공개 코스 금", "코스를 10개 공개했어요", XpSourceType.COURSE_PUBLISHED, 10),
    COURSE_PUBLISHED_RUBY("공개 코스 루비", "코스를 50개 공개했어요", XpSourceType.COURSE_PUBLISHED, 50),
    COURSE_PUBLISHED_CRYSTAL("공개 코스 크리스탈", "코스를 100개 공개했어요", XpSourceType.COURSE_PUBLISHED, 100),
    COURSE_PUBLISHED_DIAMOND("공개 코스 다이아", "코스를 500개 공개했어요", XpSourceType.COURSE_PUBLISHED, 500),

    // 코스 복사(COURSE_SHARED_COPY)
    COURSE_SHARED_BRONZE("인기 코스 동", "내가 공유한 코스가 총 1번 복사됐어요", XpSourceType.COURSE_SHARED_COPY, 1),
    COURSE_SHARED_SILVER("인기 코스 은", "내가 공유한 코스가 총 5번 복사됐어요", XpSourceType.COURSE_SHARED_COPY, 5),
    COURSE_SHARED_GOLD("인기 코스 금", "내가 공유한 코스가 총 10번 복사됐어요", XpSourceType.COURSE_SHARED_COPY, 10),
    COURSE_SHARED_RUBY("인기 코스 루비", "내가 공유한 코스가 총 50번 복사됐어요", XpSourceType.COURSE_SHARED_COPY, 50),
    COURSE_SHARED_CRYSTAL("인기 코스 크리스탈", "내가 공유한 코스가 총 100번 복사됐어요", XpSourceType.COURSE_SHARED_COPY, 100),
    COURSE_SHARED_DIAMOND("인기 코스 다이아", "내가 공유한 코스가 총 500번 복사됐어요", XpSourceType.COURSE_SHARED_COPY, 500),

    // 구원자(HELPFUL) — 본인 행동이 아니라 리뷰가 "도움됐어요"를 받은 총합이 기준이라
    // relatedSourceType이 null이고, evaluateHelpfulSaviorBadge로만 평가된다.
    HELPFUL_BRONZE("구원자 동", "내 리뷰가 다른 집사들에게 도움됐어요를 총 1번 받았어요", null, 1),
    HELPFUL_SILVER("구원자 은", "내 리뷰가 다른 집사들에게 도움됐어요를 총 5번 받았어요", null, 5),
    HELPFUL_GOLD("구원자 금", "내 리뷰가 다른 집사들에게 도움됐어요를 총 10번 받았어요", null, 10),
    HELPFUL_RUBY("구원자 루비", "내 리뷰가 다른 집사들에게 도움됐어요를 총 50번 받았어요", null, 50),
    HELPFUL_CRYSTAL("구원자 크리스탈", "내 리뷰가 다른 집사들에게 도움됐어요를 총 100번 받았어요", null, 100),
    HELPFUL_DIAMOND("구원자 다이아", "내 리뷰가 다른 집사들에게 도움됐어요를 총 500번 받았어요", null, 500);

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
