package com.freepets.domain.facility.entity;

/**
 * 반려동물 친화도 등급(발자국 등급).
 *
 * <p>기능명세서 F6-3의 등급표를 그대로 옮겼다. 응답의 등급 라벨과 레벨은 여기서만 나온다.
 * 목록·상세·리뷰 세 API가 모두 이 표를 쓴다. 임계값을 다른 곳에 또 적으면 정책이 바뀔 때
 * 한쪽만 고치게 된다.
 *
 * <p>등급은 친화도 점수만으로 정해지지 않는다. 리뷰가 3건뿐인 시설이 100점이라고 해서
 * "최고 등급"이 되면 안 되므로, 점수와 리뷰 수를 함께 본다.
 */
public enum PetFriendlyGrade {

    BEST(5, 94, 150, "최고 등급"),

    EXCELLENT(4, 88, 90, "동반 우수"),

    RECOMMENDED(3, 80, 50, "동반 추천"),

    COMFORTABLE(2, 70, 25, "동반 편안"),

    AVAILABLE(1, 60, 10, "동반 가능");

    /** 어느 등급에도 못 미쳤을 때의 레벨. 프론트는 이 값으로 발자국을 0개로 그린다. */
    public static final int NO_GRADE_LEVEL = 0;

    /** 최하위 등급에 필요한 리뷰 수. "리뷰 수집 중 (n/10)"의 분모이기도 하다. */
    public static final long MINIMUM_REVIEW_COUNT = AVAILABLE.minimumReviewCount;

    private final int level;
    private final int minimumPetScore;
    private final long minimumReviewCount;
    private final String label;

    PetFriendlyGrade(
            int level,
            int minimumPetScore,
            long minimumReviewCount,
            String label
    ) {
        this.level = level;
        this.minimumPetScore = minimumPetScore;
        this.minimumReviewCount = minimumReviewCount;
        this.label = label;
    }

    public int getLevel() {
        return level;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 리뷰에서 즉시 계산한 점수로 등급을 판정한다.
     *
     * <p>등급 상수는 높은 순으로 선언되어 있으므로 처음 만족하는 것이 곧 최고 등급이다.
     *
     * <p>표시용으로 소수 한 자리로 반올림하기 <b>전</b>의 원점수를 넘겨야 한다.
     * 87.96이 88.0으로 반올림되면서 한 등급 올라가면 안 된다.
     *
     * @return 어느 등급에도 못 미치면 {@code null}
     */
    public static PetFriendlyGrade ofScore(
            double petScore,
            long reviewCount
    ) {
        for (PetFriendlyGrade grade : values()) {
            if (petScore >= grade.minimumPetScore && reviewCount >= grade.minimumReviewCount) {
                return grade;
            }
        }

        return null;
    }

    /**
     * 미리 계산해 저장해둔 점수로 등급을 판정한다. 목록 조회가 쓴다.
     *
     * @return 어느 등급에도 못 미치거나 점수가 아직 없으면 {@code null}
     */
    public static PetFriendlyGrade of(
            Double petScore,
            long reviewCount
    ) {
        return petScore == null ? null : ofScore(petScore, reviewCount);
    }

    /**
     * 등급이 없으면 {@code null}을 내려 프론트가 라벨을 감추게 한다. 목록 조회가 쓴다.
     *
     * <p>등급이 없을 때 안내 문구를 보여줘야 하는 화면은 {@link #displayLabelOf}를 쓴다.
     */
    public static String labelOf(
            Double petScore,
            long reviewCount
    ) {
        PetFriendlyGrade grade = of(petScore, reviewCount);

        return grade == null ? null : grade.label;
    }

    /**
     * 저장된 레벨로 등급을 되찾는다. 랭킹 조회가 쓴다.
     *
     * <p>랭킹은 시설에 저장해둔 {@code pawGradeLevel}로 정렬하므로, 화면에 보여줄 등급도 같은
     * 값에서 나와야 한다. 점수로 다시 판정하면 정렬 기준과 배지가 어긋날 수 있다.
     *
     * @return {@link #NO_GRADE_LEVEL}이거나 알 수 없는 레벨이면 {@code null}
     */
    public static PetFriendlyGrade ofLevel(int level) {
        for (PetFriendlyGrade grade : values()) {
            if (grade.level == level) {
                return grade;
            }
        }

        return null;
    }

    public static int levelOf(PetFriendlyGrade grade) {
        return grade == null ? NO_GRADE_LEVEL : grade.level;
    }

    /**
     * 등급이 없으면 얼마나 모였는지 안내하는 문구를 대신 내려준다.
     *
     * <p>라벨을 감추는 {@link #labelOf}와 달리, 상세·리뷰 화면은 등급을 못 받은 이유를
     * 보여줘야 한다. {@link #levelOf}와 짝으로 쓴다.
     */
    public static String displayLabelOf(
            PetFriendlyGrade grade,
            long reviewCount
    ) {
        return grade == null ? collectingLabel(reviewCount) : grade.label;
    }

    /** 등급이 없을 때 라벨 자리에 대신 넣을 안내 문구. */
    public static String collectingLabel(long reviewCount) {
        return "리뷰 수집 중 (%d/%d)".formatted(reviewCount, MINIMUM_REVIEW_COUNT);
    }

    /** 첫 등급을 받기까지 남은 리뷰 수. */
    public static long needMore(long reviewCount) {
        return Math.max(0, MINIMUM_REVIEW_COUNT - reviewCount);
    }

}
