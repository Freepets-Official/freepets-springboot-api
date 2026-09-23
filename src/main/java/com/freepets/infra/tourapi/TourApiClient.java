package com.freepets.infra.tourapi;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

/**
 * 한국관광공사 국문 관광정보 서비스(KorService2) 호출 클라이언트.
 *
 * <p>스프링에 의존하지 않는 POJO다. 운영에서는 {@code TourApiConfig}가 빈으로 등록하고,
 * 탐사용 테스트에서는 직접 생성해 쓴다.
 *
 * <p>응답은 역직렬화하지 않고 원본 JSON 문자열을 그대로 반환한다.
 * 응답 구조를 실물로 확인하기 전이고, 탐사 단계에서는 원본 보존이 목적이기 때문이다.
 *
 * <p>호출 간 최소 간격을 두지만 그 상태는 원자적으로 관리하므로 여러 스레드가 공유해도 된다.
 * 다만 간격이 0이 아니면 동시 호출이 서로를 기다리게 되므로, 요청 경로에서 쓰는 인스턴스는
 * 간격을 0으로 만들어 쓴다({@code TourApiConfig} 참고).
 *
 * <p>실제 HTTP 호출 내역은 DEBUG로 남긴다. 배치는 수천 번을 부르므로 INFO로 두면 로그가 넘친다.
 * 확인이 필요할 때 {@code logging.level.com.freepets.infra.tourapi=DEBUG}로 켠다.
 */
@Slf4j
public class TourApiClient {

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";

    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "Freepets";
    private static final String RESPONSE_TYPE = "json";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** 공공데이터포털에 대한 호출 예절. 수천 페이지를 연달아 도는 배치의 기본 간격이다. */
    public static final long BATCH_INTERVAL_MILLIS = 250L;

    /** 요청 한 건이 호출 한 번으로 끝나는 경로에서 쓰는 간격. 대기가 곧 응답 지연이라 두지 않는다. */
    public static final long NO_INTERVAL_MILLIS = 0L;

    private final String encodedServiceKey;
    private final HttpClient httpClient;
    private final long minimumIntervalMillis;

    private final AtomicLong lastRequestedAtMillis = new AtomicLong(0L);

    /** 배치 기본 간격으로 만든다. */
    public TourApiClient(String serviceKey) {
        this(serviceKey, BATCH_INTERVAL_MILLIS);
    }

    /**
     * @param minimumIntervalMillis 연속 호출 사이에 둘 최소 간격. 0이면 기다리지 않는다
     */
    public TourApiClient(
            String serviceKey,
            long minimumIntervalMillis
    ) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalArgumentException("서비스키가 비어 있습니다.");
        }
        if (minimumIntervalMillis < 0) {
            throw new IllegalArgumentException("호출 간격은 0 이상이어야 합니다.");
        }
        this.encodedServiceKey = encodeServiceKeyIfNeeded(serviceKey);
        this.minimumIntervalMillis = minimumIntervalMillis;
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
        return areaBasedList(contentTypeId, null, null, pageNo, numOfRows);
    }

    /**
     * 지역기반 관광정보 조회를 법정동 코드로 좁힌다.
     *
     * <p>지역 코드 체계는 {@code ldongCode2}가 내려주는 것과 같고, 응답 항목의
     * {@code lDongRegnCd}/{@code lDongSignguCd}와도 같다. 그래서 우리 {@code regions} 테이블에
     * 적재해둔 코드를 변환 없이 그대로 실을 수 있다.
     *
     * @param sidoCode    법정동 시도 코드. null이면 전국을 조회한다
     * @param sigunguCode 법정동 시군구 코드. 시도 코드와 함께 보낼 때만 유효하다
     */
    public String areaBasedList(
            Integer contentTypeId,
            String sidoCode,
            String sigunguCode,
            int pageNo,
            int numOfRows
    ) {
        return areaBasedList(contentTypeId, sidoCode, sigunguCode, null, pageNo, numOfRows);
    }

    /**
     * 지역기반 관광정보 조회를 분류체계 중분류까지 좁힌다.
     *
     * <p>음식점(39)은 카페와 음식점을 {@code contentTypeId}로 가르지 못해 중분류가 필요하다.
     * 코드 체계는 {@code lclsSystmCode2}가 내려주는 것과 같다.
     *
     * @param mediumCategoryCode 분류체계 중분류 코드({@code lclsSystm2}). null이면 거르지 않는다
     */
    public String areaBasedList(
            Integer contentTypeId,
            String sidoCode,
            String sigunguCode,
            String mediumCategoryCode,
            int pageNo,
            int numOfRows
    ) {
        StringBuilder query = commonQuery(pageNo, numOfRows);
        appendParameter(query, "arrange", "C");
        appendParameter(query, "contentTypeId", contentTypeId);
        appendParameter(query, "lDongRegnCd", sidoCode);
        appendParameter(query, "lDongSignguCd", sigunguCode);
        appendParameter(query, "lclsSystm2", mediumCategoryCode);
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

        long startedAtMillis = System.currentTimeMillis();
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
        // 서비스키가 로그에 새지 않도록 쿼리는 빼고 호스트와 경로만 남긴다.
        log.debug("관광공사 API 호출. GET {}{} → HTTP {}, 응답크기={}자, 소요={}ms",
                uri.getHost(), uri.getPath(), httpResponse.statusCode(),
                body == null ? 0 : body.length(), System.currentTimeMillis() - startedAtMillis);
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

    /**
     * 직전 호출로부터 최소 간격이 지날 때까지 기다린다.
     *
     * <p>여러 스레드가 한 인스턴스를 공유할 수 있으므로 자기 차례를 먼저 원자적으로 예약하고
     * 나서 잔다. 읽고 자고 쓰는 순서로 하면 동시에 들어온 호출들이 같은 시각을 읽고 함께 나간다.
     */
    private void waitForMinimumInterval() {
        if (minimumIntervalMillis == NO_INTERVAL_MILLIS) {
            return;
        }

        long now = System.currentTimeMillis();
        long sendAtMillis = lastRequestedAtMillis.accumulateAndGet(
                now,
                (previousSendAt, current) -> Math.max(current, previousSendAt + minimumIntervalMillis)
        );

        long waitMillis = sendAtMillis - now;
        if (waitMillis <= 0) {
            return;
        }

        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TourApiException("호출 대기가 중단되었습니다.", exception);
        }
    }

}
