package com.freepets.domain.gamification.entity;

/**
 * 행동 기반 배지 카탈로그. 레벨 배지({@link LevelTier})와 별개로, 특정 행동을 달성하면 준다.
 *
 * <p>대부분의 도메인은 동일한 6단계(1·5·10·50·100·500회)로 나뉘고, 단계 이름도 동/은/금/루비/
 * 크리스탈/다이아로 통일한다 — 도메인별로 다른 개수·다른 이름을 쓰던 이전 방식 대신, 어느
 * 도메인이든 "동=1회, 다이아=500회"라는 것만 기억하면 되게 한다. {@code relatedSourceType}(=
 * {@link #getFamily()}{@code .getRelatedSourceType()})은
 * {@link com.freepets.domain.gamification.service.BadgeEvaluationService#evaluateAfterXpEvent}가
 * "이 타입의 XpEvent가 막 하나 생겼을 때만 이 배지를 재평가하면 된다"는 걸 판단하는 용도고,
 * {@code threshold}(=기본은 {@link #getTier()}{@code .getThreshold()}, {@code REGION}처럼 예외가
 * 있으면 {@code thresholdOverride})는 그 타입의 누적 달성 횟수가 몇 번째에 달성되는지다. 상수마다
 * 따로 저장하지 않는다 — 같은 패밀리·같은 단계면 항상 같은 값이라, {@code family}/{@code tier}
 * 필드로만 정해지게 해서 상수 사이에 값이 어긋날 여지를 없앤다.
 *
 * <p>{@code REGION}(정복자)은 예외다 — 시/군/구는 시설보다 훨씬 느리게 늘어 공통 6단계 기준을
 * 그대로 쓰면 다이아(500곳)가 불가능하므로, {@code thresholdOverride}로 1/5/15/30 4단계만 쓴다
 * (CRYSTAL·DIAMOND 상수 자체가 없다).
 *
 * <p>{@code HELPFUL_*}·{@code STAMP_*}·{@code REGION_*}처럼 패밀리의 {@code relatedSourceType}이
 * {@code null}인 배지는 XpEvent 기반 평가 대상이 아니다 — 본인의 판별·리뷰 같은 행동이 아니라
 * 남의 반응(도움됐어요)이거나 다른 도메인(stamp)의 누적치가 트리거라, 각자 별도 평가 경로
 * ({@link com.freepets.domain.gamification.service.BadgeEvaluationService#evaluateHelpfulSaviorBadge},
 * {@link com.freepets.domain.gamification.service.BadgeEvaluationService#evaluateStampBadge},
 * {@link com.freepets.domain.gamification.service.BadgeEvaluationService#evaluateRegionBadge})를 탄다.
 */
public enum Badge {

    // 판별(PETCHECK)
    PETCHECK_BRONZE("판별 동", "판별 기능을 1번 이용했어요", BadgeFamily.PETCHECK, BadgeTier.BRONZE),
    PETCHECK_SILVER("판별 은", "판별 기능을 5번 이용했어요", BadgeFamily.PETCHECK, BadgeTier.SILVER),
    PETCHECK_GOLD("판별 금", "판별 기능을 10번 이용했어요", BadgeFamily.PETCHECK, BadgeTier.GOLD),
    PETCHECK_RUBY("판별 루비", "판별 기능을 50번 이용했어요", BadgeFamily.PETCHECK, BadgeTier.RUBY),
    PETCHECK_CRYSTAL("판별 크리스탈", "판별 기능을 100번 이용했어요", BadgeFamily.PETCHECK, BadgeTier.CRYSTAL),
    PETCHECK_DIAMOND("판별 다이아", "판별 기능을 500번 이용했어요", BadgeFamily.PETCHECK, BadgeTier.DIAMOND),

    // 리뷰(REVIEW)
    REVIEW_BRONZE("리뷰 동", "리뷰를 1개 작성했어요", BadgeFamily.REVIEW, BadgeTier.BRONZE),
    REVIEW_SILVER("리뷰 은", "리뷰를 5개 작성했어요", BadgeFamily.REVIEW, BadgeTier.SILVER),
    REVIEW_GOLD("리뷰 금", "리뷰를 10개 작성했어요", BadgeFamily.REVIEW, BadgeTier.GOLD),
    REVIEW_RUBY("리뷰 루비", "리뷰를 50개 작성했어요", BadgeFamily.REVIEW, BadgeTier.RUBY),
    REVIEW_CRYSTAL("리뷰 크리스탈", "리뷰를 100개 작성했어요", BadgeFamily.REVIEW, BadgeTier.CRYSTAL),
    REVIEW_DIAMOND("리뷰 다이아", "리뷰를 500개 작성했어요", BadgeFamily.REVIEW, BadgeTier.DIAMOND),

    // 제보(REPORT)
    REPORT_BRONZE("제보 동", "거부 제보를 1번 남겼어요", BadgeFamily.REPORT, BadgeTier.BRONZE),
    REPORT_SILVER("제보 은", "거부 제보를 5번 남겼어요", BadgeFamily.REPORT, BadgeTier.SILVER),
    REPORT_GOLD("제보 금", "거부 제보를 10번 남겼어요", BadgeFamily.REPORT, BadgeTier.GOLD),
    REPORT_RUBY("제보 루비", "거부 제보를 50번 남겼어요", BadgeFamily.REPORT, BadgeTier.RUBY),
    REPORT_CRYSTAL("제보 크리스탈", "거부 제보를 100번 남겼어요", BadgeFamily.REPORT, BadgeTier.CRYSTAL),
    REPORT_DIAMOND("제보 다이아", "거부 제보를 500번 남겼어요", BadgeFamily.REPORT, BadgeTier.DIAMOND),

    // 만족도(SATISFACTION)
    SATISFACTION_BRONZE("만족도 동", "만족도를 1번 남겼어요", BadgeFamily.SATISFACTION, BadgeTier.BRONZE),
    SATISFACTION_SILVER("만족도 은", "만족도를 5번 남겼어요", BadgeFamily.SATISFACTION, BadgeTier.SILVER),
    SATISFACTION_GOLD("만족도 금", "만족도를 10번 남겼어요", BadgeFamily.SATISFACTION, BadgeTier.GOLD),
    SATISFACTION_RUBY("만족도 루비", "만족도를 50번 남겼어요", BadgeFamily.SATISFACTION, BadgeTier.RUBY),
    SATISFACTION_CRYSTAL("만족도 크리스탈", "만족도를 100번 남겼어요", BadgeFamily.SATISFACTION, BadgeTier.CRYSTAL),
    SATISFACTION_DIAMOND("만족도 다이아", "만족도를 500번 남겼어요", BadgeFamily.SATISFACTION, BadgeTier.DIAMOND),

    // 코스 공개(COURSE_PUBLISHED)
    COURSE_PUBLISHED_BRONZE("공개 코스 동", "코스를 1개 공개했어요", BadgeFamily.COURSE_PUBLISHED, BadgeTier.BRONZE),
    COURSE_PUBLISHED_SILVER("공개 코스 은", "코스를 5개 공개했어요", BadgeFamily.COURSE_PUBLISHED, BadgeTier.SILVER),
    COURSE_PUBLISHED_GOLD("공개 코스 금", "코스를 10개 공개했어요", BadgeFamily.COURSE_PUBLISHED, BadgeTier.GOLD),
    COURSE_PUBLISHED_RUBY("공개 코스 루비", "코스를 50개 공개했어요", BadgeFamily.COURSE_PUBLISHED, BadgeTier.RUBY),
    COURSE_PUBLISHED_CRYSTAL("공개 코스 크리스탈", "코스를 100개 공개했어요", BadgeFamily.COURSE_PUBLISHED, BadgeTier.CRYSTAL),
    COURSE_PUBLISHED_DIAMOND("공개 코스 다이아", "코스를 500개 공개했어요", BadgeFamily.COURSE_PUBLISHED, BadgeTier.DIAMOND),

    // 코스 복사(COURSE_SHARED_COPY)
    COURSE_SHARED_BRONZE("인기 코스 동", "내가 공유한 코스가 총 1번 복사됐어요", BadgeFamily.COURSE_SHARED, BadgeTier.BRONZE),
    COURSE_SHARED_SILVER("인기 코스 은", "내가 공유한 코스가 총 5번 복사됐어요", BadgeFamily.COURSE_SHARED, BadgeTier.SILVER),
    COURSE_SHARED_GOLD("인기 코스 금", "내가 공유한 코스가 총 10번 복사됐어요", BadgeFamily.COURSE_SHARED, BadgeTier.GOLD),
    COURSE_SHARED_RUBY("인기 코스 루비", "내가 공유한 코스가 총 50번 복사됐어요", BadgeFamily.COURSE_SHARED, BadgeTier.RUBY),
    COURSE_SHARED_CRYSTAL("인기 코스 크리스탈", "내가 공유한 코스가 총 100번 복사됐어요", BadgeFamily.COURSE_SHARED, BadgeTier.CRYSTAL),
    COURSE_SHARED_DIAMOND("인기 코스 다이아", "내가 공유한 코스가 총 500번 복사됐어요", BadgeFamily.COURSE_SHARED, BadgeTier.DIAMOND),

    // 구원자(HELPFUL) — 본인 행동이 아니라 리뷰가 "도움됐어요"를 받은 총합이 기준이라
    // BadgeFamily.HELPFUL.getRelatedSourceType()이 null이고, evaluateHelpfulSaviorBadge로만
    // 평가된다.
    HELPFUL_BRONZE("구원자 동", "내 리뷰가 다른 집사들에게 도움됐어요를 총 1번 받았어요", BadgeFamily.HELPFUL, BadgeTier.BRONZE),
    HELPFUL_SILVER("구원자 은", "내 리뷰가 다른 집사들에게 도움됐어요를 총 5번 받았어요", BadgeFamily.HELPFUL, BadgeTier.SILVER),
    HELPFUL_GOLD("구원자 금", "내 리뷰가 다른 집사들에게 도움됐어요를 총 10번 받았어요", BadgeFamily.HELPFUL, BadgeTier.GOLD),
    HELPFUL_RUBY("구원자 루비", "내 리뷰가 다른 집사들에게 도움됐어요를 총 50번 받았어요", BadgeFamily.HELPFUL, BadgeTier.RUBY),
    HELPFUL_CRYSTAL("구원자 크리스탈", "내 리뷰가 다른 집사들에게 도움됐어요를 총 100번 받았어요", BadgeFamily.HELPFUL, BadgeTier.CRYSTAL),
    HELPFUL_DIAMOND("구원자 다이아", "내 리뷰가 다른 집사들에게 도움됐어요를 총 500번 받았어요", BadgeFamily.HELPFUL, BadgeTier.DIAMOND),

    // 여권 도장(STAMP) — 다른 도메인과 같은 6단계(1/5/10/50/100/500)를 그대로 쓴다
    // (freepets-docs docs/14-도장-서버-저장.md 3절 "다른 도메인과 같은 1/5/10/50/100/500").
    STAMP_BRONZE("도장 동", "여권 도장을 1개 모았어요", BadgeFamily.STAMP, BadgeTier.BRONZE),
    STAMP_SILVER("도장 은", "여권 도장을 5개 모았어요", BadgeFamily.STAMP, BadgeTier.SILVER),
    STAMP_GOLD("도장 금", "여권 도장을 10개 모았어요", BadgeFamily.STAMP, BadgeTier.GOLD),
    STAMP_RUBY("도장 루비", "여권 도장을 50개 모았어요", BadgeFamily.STAMP, BadgeTier.RUBY),
    STAMP_CRYSTAL("도장 크리스탈", "여권 도장을 100개 모았어요", BadgeFamily.STAMP, BadgeTier.CRYSTAL),
    STAMP_DIAMOND("도장 다이아", "여권 도장을 500개 모았어요", BadgeFamily.STAMP, BadgeTier.DIAMOND),

    // 정복자(REGION) — 서로 다른 시/군/구 수가 기준. 지역은 시설보다 훨씬 느리게 늘어 다른
    // 도메인과 같은 문턱(1/5/10/50/100/500)을 쓰면 다이아(500곳)가 사실상 불가능하다 —
    // 문서가 명시한 1/5/15/30 4단계만 쓰고 CRYSTAL·DIAMOND는 만들지 않는다.
    REGION_BRONZE("정복자 동", "도장을 찍은 시/군/구가 1곳이에요", BadgeFamily.REGION, BadgeTier.BRONZE, 1),
    REGION_SILVER("정복자 은", "도장을 찍은 시/군/구가 5곳이에요", BadgeFamily.REGION, BadgeTier.SILVER, 5),
    REGION_GOLD("정복자 금", "도장을 찍은 시/군/구가 15곳이에요", BadgeFamily.REGION, BadgeTier.GOLD, 15),
    REGION_RUBY("정복자 루비", "도장을 찍은 시/군/구가 30곳이에요", BadgeFamily.REGION, BadgeTier.RUBY, 30);

    private final String label;
    private final String description;
    private final BadgeFamily family;
    private final BadgeTier tier;

    /** null이면 {@link #getThreshold()}가 {@code tier.getThreshold()}를 쓴다. REGION처럼 공통
     *  6단계 기준을 그대로 못 쓰는 패밀리만 이 값을 채운다. */
    private final Integer thresholdOverride;

    Badge(
            String label,
            String description,
            BadgeFamily family,
            BadgeTier tier
    ) {
        this(label, description, family, tier, null);
    }

    Badge(
            String label,
            String description,
            BadgeFamily family,
            BadgeTier tier,
            Integer thresholdOverride
    ) {
        this.label = label;
        this.description = description;
        this.family = family;
        this.tier = tier;
        this.thresholdOverride = thresholdOverride;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public BadgeFamily getFamily() {
        return family;
    }

    public BadgeTier getTier() {
        return tier;
    }

    public XpSourceType getRelatedSourceType() {
        return family.getRelatedSourceType();
    }

    public int getThreshold() {
        return thresholdOverride != null ? thresholdOverride : tier.getThreshold();
    }

}
