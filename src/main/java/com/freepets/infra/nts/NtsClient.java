package com.freepets.infra.nts;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 국세청 사업자등록정보 <b>진위확인</b> 호출 클라이언트.
 *
 * <p>상태조회(번호만 보내는 쪽)는 쓰지 않는다. 번호는 영수증에도 찍혀 있어 누구나 통과하기 때문이다.
 * 진위확인은 번호·대표자 성명·개업일자가 모두 맞아야 통과한다.
 *
 * <p>일치하면 응답에 사업 상태(휴업·폐업)까지 함께 오므로 호출은 한 번으로 끝난다.
 *
 * <p>스프링에 의존하지 않는 POJO다. 운영에서는 {@code NtsConfig}가 빈으로 등록한다.
 *
 * <p><b>사업자등록번호를 로그에 남기지 않는다.</b> 예외 메시지에도 싣지 않는다.
 */
public class NtsClient {

    private static final String VALIDATE_URL = "https://api.odcloud.kr/api/nts-businessman/v1/validate";

    /** 진위확인 일치 코드. 불일치는 {@code 02}이고 사유가 {@code valid_msg}에 담긴다. */
    private static final String MATCHED = "01";

    private final NtsApiCaller apiCaller;
    private final String encodedServiceKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NtsClient(
            NtsApiCaller apiCaller,
            String serviceKey
    ) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalArgumentException("국세청 서비스키가 비어 있습니다.");
        }
        this.apiCaller = apiCaller;
        this.encodedServiceKey = encodeServiceKeyIfNeeded(serviceKey);
    }

    /**
     * @param businessNumber     하이픈 없는 사업자등록번호 10자리
     * @param representativeName 대표자 성명
     * @param openingDate        개업일자 (YYYYMMDD)
     * @throws NtsException 국세청 통신 실패, 또는 응답에서 사업자 정보를 찾지 못한 경우
     */
    public NtsValidationResult validate(
            String businessNumber,
            String representativeName,
            String openingDate
    ) {
        String requestBody = buildRequestBody(businessNumber, representativeName, openingDate);
        JsonNode response = apiCaller.postJson(
                VALIDATE_URL + "?serviceKey=" + encodedServiceKey + "&returnType=JSON",
                requestBody
        );

        JsonNode business = response.path("data").path(0);
        if (business.isMissingNode() || business.isEmpty()) {
            throw new NtsException(
                    "진위확인 응답에 사업자 정보가 없습니다. status_code=" + response.path("status_code").asText("-")
            );
        }

        // 불일치(valid=02)면 status가 없을 수 있다. 그때는 상태를 보지 않고 서비스가 불일치로 처리한다.
        JsonNode status = business.path("status");
        return new NtsValidationResult(
                MATCHED.equals(business.path("valid").asText("")),
                business.path("valid_msg").asText(""),
                status.path("b_stt_cd").asText(""),
                status.path("b_stt").asText("")
        );
    }

    /**
     * 필수 3개({@code b_no}·{@code start_dt}·{@code p_nm})만 싣는다.
     *
     * <p>선택 항목은 넣지 않는다. 특히 대표자성명2({@code p_nm2})는 외국인 사업자 전용이라
     * 내국인 사업자 요청에 넣으면 오히려 진위확인이 실패한다(공식 문서 「API 요청항목별 유의사항」).
     * 그래서 <b>외국인 사업자는 현재 이 API로 인증할 수 없다</b> — 지원하려면 대표자성명2를
     * 받는 경로를 따로 만들어야 한다.
     */
    private String buildRequestBody(
            String businessNumber,
            String representativeName,
            String openingDate
    ) {
        ObjectNode business = objectMapper.createObjectNode();
        business.put("b_no", businessNumber);
        business.put("start_dt", openingDate);
        business.put("p_nm", representativeName);

        ObjectNode root = objectMapper.createObjectNode();
        root.set("businesses", objectMapper.createArrayNode().add(business));

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new NtsException("진위확인 요청을 JSON으로 만들지 못했습니다.", exception);
        }
    }

    /**
     * 공공데이터포털은 인코딩된 키와 디코딩된 키를 함께 발급한다. 인코딩된 키를 다시 인코딩하면
     * 서비스키 오류가 나므로, 이미 인코딩된 키는 그대로 두고 디코딩된 키만 인코딩한다
     * ({@code TourApiClient}와 같은 처리).
     */
    private String encodeServiceKeyIfNeeded(String serviceKey) {
        String trimmed = serviceKey.trim();
        boolean isAlreadyEncoded = trimmed.contains("%");
        return isAlreadyEncoded ? trimmed : URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
    }
}
