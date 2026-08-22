package com.freepets.infra.tourapi;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 한국관광공사 국문 관광정보 서비스(KorService2) 호출 클라이언트.
 *
 * <p>스프링에 의존하지 않는 POJO다. 운영에서는 {@code TourApiConfig}가 빈으로 등록하고,
 * 탐사용 테스트에서는 직접 생성해 쓴다.
 *
 * <p>응답은 역직렬화하지 않고 원본 JSON 문자열을 그대로 반환한다.
 * 응답 구조를 실물로 확인하기 전이고, 탐사 단계에서는 원본 보존이 목적이기 때문이다.
 *
 * <p>호출 간 최소 간격을 두는 상태를 가지므로 스레드 안전하지 않다. 단일 스레드 배치에서만 사용한다.
 */
public class TourApiClient {

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";

    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "Freepets";
    private static final String RESPONSE_TYPE = "json";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** 공공데이터포털에 대한 호출 예절. 연속 호출 사이 최소 간격이다. */
    private static final long MINIMUM_INTERVAL_MILLIS = 250L;

    private final String encodedServiceKey;
    private final HttpClient httpClient;

    private long lastRequestedAtMillis = 0L;

    public TourApiClient(String serviceKey) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalArgumentException("서비스키가 비어 있습니다.");
        }
        this.encodedServiceKey = encodeServiceKeyIfNeeded(serviceKey);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * 지역기반 관광정보 조회. 전체 시설 집합(A)을 확보한다.
     *
     * @param contentTypeId 관광타입 ID. null이면 전체 타입을 조회한다
     */
    public String areaBasedList(
            Integer contentTypeId,
            int pageNo,
            int numOfRows
    ) {
        StringBuilder query = commonQuery(pageNo, numOfRows);
        appendParameter(query, "arrange", "C");
        appendParameter(query, "contentTypeId", contentTypeId);
        return request("areaBasedList2", query);
    }

    /**
     * 반려동물 동반여행 정보 조회. 펫 정보 보유 집합(B)을 확보한다.
     *
     * @param contentId 콘텐츠 ID. <b>null이면 반려동물 동반 정보를 보유한 전체 목록</b>이 반환된다
     */
    public String detailPetTour(
            String contentId,
            int pageNo,
            int numOfRows
    ) {
        StringBuilder query = commonQuery(pageNo, numOfRows);
        appendParameter(query, "contentId", contentId);
        return request("detailPetTour2", query);
    }

    /**
     * 공통정보 조회. 특정 콘텐츠가 KorService2에 존재하는지 확인하는 용도로 쓴다.
     * 적재 대상은 아니다(상세 응답에 대응 필드가 없다).
     */
    public String detailCommon(String contentId) {
        StringBuilder query = commonQuery(1, 1);
        appendParameter(query, "contentId", contentId);
        return request("detailCommon2", query);
    }

    /**
     * 법정동 코드 조회. 시도·시군구 코드를 이름으로 옮기는 매핑표를 만든다.
     *
     * @param sidoCode  시도 코드. null이면 전체 시도 목록을 반환한다
     * @param wholeList true면 시도-시군구 매핑 전체를 한 번에 받는다
     */
    public String ldongCode(
            String sidoCode,
            boolean wholeList,
            int pageNo,
            int numOfRows
    ) {
        StringBuilder query = commonQuery(pageNo, numOfRows);
        appendParameter(query, "lDongRegnCd", sidoCode);
        appendParameter(query, "lDongListYn", wholeList ? "Y" : "N");
        return request("ldongCode2", query);
    }

    /**
     * 분류체계 코드 조회. 음식점(39)을 음식점/카페로 가르는 기준을 찾는 데 쓴다.
     */
    public String lclsSystmCode(
            boolean wholeList,
            int pageNo,
            int numOfRows
    ) {
        StringBuilder query = commonQuery(pageNo, numOfRows);
        appendParameter(query, "lclsSystmListYn", wholeList ? "Y" : "N");
        return request("lclsSystmCode2", query);
    }

    /**
     * 국문 관광정보 동기화 목록 조회. 기본정보 증분 동기화에 쓴다.
     *
     * @param modifiedTime 변경 기준일자(YYYYMMDD)
     * @param showFlag     1=표출, 0=비표출. null이면 전체
     */
    public String areaBasedSyncList(
            String modifiedTime,
            Integer showFlag,
            int pageNo,
            int numOfRows
    ) {
        StringBuilder query = commonQuery(pageNo, numOfRows);
        appendParameter(query, "modifiedtime", modifiedTime);
        appendParameter(query, "showflag", showFlag);
        return request("areaBasedSyncList2", query);
    }

    private StringBuilder commonQuery(
            int pageNo,
            int numOfRows
    ) {
        StringBuilder query = new StringBuilder();
        appendParameter(query, "serviceKey", encodedServiceKey);
        appendParameter(query, "MobileOS", MOBILE_OS);
        appendParameter(query, "MobileApp", MOBILE_APP);
        appendParameter(query, "_type", RESPONSE_TYPE);
        appendParameter(query, "pageNo", pageNo);
        appendParameter(query, "numOfRows", numOfRows);
        return query;
    }

    private String request(
            String operation,
            StringBuilder query
    ) {
        waitForMinimumInterval();

        URI uri = URI.create(BASE_URL + "/" + operation + "?" + query);
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> httpResponse;
        try {
            httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new TourApiException(operation + " 호출에 실패했습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TourApiException(operation + " 호출이 중단되었습니다.", exception);
        }

        String body = httpResponse.body();
        verifyNotErrorResponse(operation, httpResponse.statusCode(), body);
        return body;
    }

    /**
     * 공공데이터포털은 {@code _type=json}을 붙여도 인증 실패·한도 초과 시 XML로 응답한다.
     * JSON 파서에 그대로 넘기면 원인 불명의 예외가 나므로 여기서 먼저 걸러낸다.
     */
    private void verifyNotErrorResponse(
            String operation,
            int statusCode,
            String body
    ) {
        if (body == null || body.isBlank()) {
            throw new TourApiException(operation + " 응답이 비어 있습니다. (HTTP " + statusCode + ")");
        }

        String trimmed = body.stripLeading();
        if (!trimmed.startsWith("<")) {
            return;
        }

        String reasonCode = extractXmlTagValue(trimmed, "returnReasonCode");
        String authMessage = extractXmlTagValue(trimmed, "returnAuthMsg");
        String errorMessage = extractXmlTagValue(trimmed, "errMsg");

        throw new TourApiException(
                operation + " 호출이 거부되었습니다."
                        + " reasonCode=" + reasonCode
                        + ", authMsg=" + authMessage
                        + ", errMsg=" + errorMessage
                        + " (30=서비스키 오류, 22=일일 한도 초과, 32=미등록 IP)"
        );
    }

    private String extractXmlTagValue(
            String xml,
            String tagName
    ) {
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";

        int start = xml.indexOf(openTag);
        if (start < 0) {
            return "-";
        }
        int end = xml.indexOf(closeTag, start);
        if (end < 0) {
            return "-";
        }
        return xml.substring(start + openTag.length(), end).trim();
    }

    /**
     * 공공데이터포털은 인코딩된 키와 디코딩된 키를 함께 발급한다.
     * 인코딩된 키를 다시 인코딩하면 {@code returnReasonCode 30}이 나므로,
     * 이미 인코딩된 키는 그대로 두고 디코딩된 키만 인코딩한다.
     */
    private String encodeServiceKeyIfNeeded(String serviceKey) {
        String trimmed = serviceKey.trim();
        boolean isAlreadyEncoded = trimmed.contains("%");
        return isAlreadyEncoded ? trimmed : URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
    }

    private void appendParameter(
            StringBuilder query,
            String name,
            Object value
    ) {
        if (value == null) {
            return;
        }
        if (!query.isEmpty()) {
            query.append('&');
        }
        query.append(name).append('=').append(value);
    }

    private void waitForMinimumInterval() {
        long elapsed = System.currentTimeMillis() - lastRequestedAtMillis;
        if (elapsed < MINIMUM_INTERVAL_MILLIS) {
            try {
                Thread.sleep(MINIMUM_INTERVAL_MILLIS - elapsed);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new TourApiException("호출 대기가 중단되었습니다.", exception);
            }
        }
        lastRequestedAtMillis = System.currentTimeMillis();
    }

}
