package com.freepets.domain.facility.entity;

/**
 * 반려동물 친화도 등급(발자국 등급).
 *
 * <p>기능명세서 F6-3의 등급표를 그대로 옮겼다. 응답의 {@code rating} 문자열은 여기서만 나온다.
 *
 * <p>등급은 친화도 점수만으로 정해지지 않는다. 리뷰가 3건뿐인 시설이 100점이라고 해서
 * "최고 등급"이 되면 안 되므로, 점수와 리뷰 수를 함께 본다.
 */
public enum PetFriendlyGrade {

    BEST(94, 150, "최고 등급"),

    EXCELLENT(88, 90, "동반 우수"),

    RECOMMENDED(80, 50, "동반 추천"),

    COMFORTABLE(70, 25, "동반 편안"),

    AVAILABLE(60, 10, "동반 가능");

    private final int minimumPetScore;
    private final long minimumReviewCount;
    private final String label;

    PetFriendlyGrade(
            int minimumPetScore,
            long minimumReviewCount,
            String label
    ) {
        this.minimumPetScore = minimumPetScore;
        this.minimumReviewCount = minimumReviewCount;
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 두 조건을 모두 만족하는 가장 높은 등급을 찾는다.
     *
     * <p>등급 상수는 높은 순으로 선언되어 있으므로 처음 만족하는 것이 곧 최고 등급이다.
     *
     * @return 어느 등급에도 못 미치거나 점수가 아직 없으면 {@code null}
     */
    public static PetFriendlyGrade of(
            Integer petScore,
            long reviewCount
    ) {
        if (petScore == null) {
            return null;
        }

        for (PetFriendlyGrade grade : values()) {
            if (petScore >= grade.minimumPetScore && reviewCount >= grade.minimumReviewCount) {
                return grade;
            }
        }

        return null;
    }

    /** 등급이 없으면 {@code null}을 내려 프론트가 라벨을 감추게 한다. */
    public static String labelOf(
            Integer petScore,
            long reviewCount
    ) {
        PetFriendlyGrade grade = of(petScore, reviewCount);

        return grade == null ? null : grade.label;
    }

}
