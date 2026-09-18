package com.freepets.domain.facility.service;

/**
 * 두 시설명이 같은 매장을 가리킬 만큼 비슷한지 판정한다. 신규 매장 등록 전 중복 후보를 고를 때 쓴다
 * ({@code FacilityDuplicateCandidateQueryService}).
 *
 * <p>SQL {@code LIKE}는 부분일치만 가능해 "OO카페"와 "오오카페" 같은 표기 차이를 못 잡는다. 그렇다고
 * 완전 일치만 보면 "OO카페 본점"과 "OO카페"처럼 흔한 변형을 놓친다. 그래서 반경 검색으로 이미 좁혀진
 * 소수 후보에 한해 정규화 후 포함관계·편집거리를 자바에서 직접 비교한다 — 전체 시설 대상 쿼리가 아니라
 * 외부 라이브러리 없이 순수 자바로 충분하다.
 */
public class FacilityNameMatcher {

    /** 이 이하의 편집거리는 오탈자·표기 차이로 본다. 완전히 다른 이름까지 걸리지 않도록 짧게 잡는다. */
    private static final int MAX_EDIT_DISTANCE = 2;

    private FacilityNameMatcher() {}

    public static boolean isSimilar(
            String left,
            String right
    ) {
        if (left == null || right == null) {
            return false;
        }

        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);

        if (normalizedLeft.isEmpty() || normalizedRight.isEmpty()) {
            return false;
        }

        if (normalizedLeft.equals(normalizedRight)) {
            return true;
        }

        if (normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft)) {
            return true;
        }

        return editDistance(normalizedLeft, normalizedRight) <= MAX_EDIT_DISTANCE;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase().replaceAll("\\s+", "");
    }

    /** 표준 Levenshtein 거리(삽입·삭제·치환 각 비용 1). */
    private static int editDistance(
            String left,
            String right
    ) {
        int[] previousRow = new int[right.length() + 1];
        int[] currentRow = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            previousRow[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            currentRow[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitutionCost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                currentRow[j] = Math.min(
                        Math.min(currentRow[j - 1] + 1, previousRow[j] + 1),
                        previousRow[j - 1] + substitutionCost
                );
            }
            System.arraycopy(currentRow, 0, previousRow, 0, currentRow.length);
        }

        return previousRow[right.length()];
    }

}
