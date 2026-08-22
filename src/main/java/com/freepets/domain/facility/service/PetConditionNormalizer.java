package com.freepets.domain.facility.service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 관광공사 조건 원문을 비교 가능한 형태로 다듬는다.
 *
 * <p>정규화 없이 {@code isEmpty()}만 쓰면 판정이 틀어진다. 공공데이터는 "비어 있음"의 표현이
 * 제각각이고, 실데이터에서 HTML 태그와 엔티티가 섞여 들어오는 경우도 확인됐다.
 */
public final class PetConditionNormalizer {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** 값이 있는 것처럼 보이지만 실제로는 미기재인 표현들. */
    private static final List<String> BLANK_EXPRESSIONS =
            List.of("-", "--", "없음", "해당없음", "정보없음", "미정", "N/A", "n/a");

    private PetConditionNormalizer() {}

    /**
     * 태그 제거 → 엔티티 디코딩 → 공백 정리 → 미기재 표현 대조 순으로 처리한다.
     *
     * @return 정규화된 문자열. 미기재로 판단되면 빈 문자열
     */
    public static String normalize(String rawValue) {
        if (rawValue == null) {
            return "";
        }

        String normalized = HTML_TAG.matcher(rawValue).replaceAll(" ");
        normalized = decodeEntities(normalized);
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ").trim();

        return BLANK_EXPRESSIONS.contains(normalized) ? "" : normalized;
    }

    public static boolean isBlank(String rawValue) {
        return normalize(rawValue).isEmpty();
    }

    private static String decodeEntities(String value) {
        return value
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }

}
