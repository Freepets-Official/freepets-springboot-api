package com.freepets.infra.oauth;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 소셜 응답 JSON에서 값을 안전하게 꺼낸다.
 *
 * <p>{@code JsonNode.asText(null)}에 기대지 않는다. JSON {@code null}에 대해 Jackson 버전에 따라
 * 문자열 {@code "null"}이 돌아올 수 있고, 그 값이 그대로 이메일·이름 컬럼에 저장되면
 * 원인을 찾기 어려운 데이터 오염이 된다. 여기서는 타입을 직접 확인한다.
 *
 * <p>소셜 응답은 동의 거부 항목을 빈 문자열로 주는 경우도 있어 공백은 없는 값으로 취급한다.
 */
final class OAuthJson {

    private OAuthJson() {}

    /**
     * @param path 중첩 키 경로. 예: {@code textOrNull(root, "kakao_account", "profile", "nickname")}
     * @return 문자열·숫자 노드면 그 값, 그 외(없음·null·객체·배열·공백)면 null
     */
    static String textOrNull(
            JsonNode root,
            String... path
    ) {
        JsonNode node = root;
        for (String key : path) {
            node = node.path(key);
        }

        if (!node.isTextual() && !node.isNumber()) {
            return null;
        }

        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }
}
