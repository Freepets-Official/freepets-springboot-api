package com.freepets.infra.tourapi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 관광공사 KorService2 탐사용 일회성 테스트.
 *
 * <p>실제 외부 API를 호출하므로 {@code test} 태스크에서는 실행되지 않는다.
 * 전용 태스크로만 돌린다.
 *
 * <pre>
 * ./gradlew tourApiProbe
 * ./gradlew tourApiProbe --tests "*전체_규모*"
 * </pre>
 *
 * <p>인증키는 {@code application.yml}의 {@code tour-api.service-key}에서 읽는다.
 * 환경변수 {@code TOUR_API_SERVICE_KEY}가 있으면 그쪽이 우선한다.
 * 국문 관광정보 서비스(KorService2) 키여야 하며, 반려동물 동반여행 서비스 키와는 별개다.
 *
 * <p>산출물은 {@code build/tourapi-probe/} 아래에 남는다. {@code build/}는 gitignore 대상이다.
 */
@EnabledIfSystemProperty(
        named = "tourapi.probe",
        matches = "true",
        disabledReason = "탐사 전용 테스트. ./gradlew tourApiProbe 로 실행한다."
)
class TourApiProbeTest {

    private static final Path OUTPUT_DIRECTORY = Path.of("build", "tourapi-probe");

    /** 관광공사 콘텐츠 타입. 25(여행코스)는 시설이 아니라 적재 대상에서 제외한다. */
    private static final Map<Integer, String> CONTENT_TYPES = new LinkedHashMap<>();

    static {
        CONTENT_TYPES.put(12, "관광지");
        CONTENT_TYPES.put(14, "문화시설");
        CONTENT_TYPES.put(15, "축제/공연/행사");
        CONTENT_TYPES.put(25, "여행코스(적재 제외)");
        CONTENT_TYPES.put(28, "레포츠");
        CONTENT_TYPES.put(32, "숙박");
        CONTENT_TYPES.put(38, "쇼핑");
        CONTENT_TYPES.put(39, "음식점");
        CONTENT_TYPES.put(77, "교통");
    }

    /** detailPetTour2의 조건 4필드. petAllowed 판정의 입력이다. */
    private static final List<String> CONDITION_FIELDS = List.of(
            "acmpyTypeCd",
            "acmpyPsblCpam",
            "acmpyNeedMtr",
            "etcAcmpyInfo"
    );

    /** 정규화 전에도 "비어 있음"으로 봐야 하는 표현들. 실태는 P7에서 확인한다. */
    private static final List<String> BLANK_EXPRESSIONS = List.of(
            "-", "없음", "해당없음", "정보없음", "미정", "N/A"
    );

    private static final Pattern WEIGHT_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:kg|㎏|킬로)");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final TourApiClient tourApiClient = new TourApiClient(serviceKey());

    // ------------------------------------------------------------------
    // P1 · P2 · P3
    // ------------------------------------------------------------------

    @Test
    @DisplayName("P1·P2·P3 — 서비스키 유효성과 콘텐츠 타입별 전체 규모를 확인한다")
    void 서비스키_유효성과_타입별_전체_규모를_확인한다() throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# P1·P2·P3 — 서비스키 유효성 / 전체 규모\n\n");

        JsonNode probe = call(() -> tourApiClient.areaBasedList(null, 1, 1));
        String resultCode = probe.path("response").path("header").path("resultCode").asText();
        int totalCount = totalCountOf(probe);

        report.append("- resultCode: `").append(resultCode).append("`\n");
        report.append("- **전체 건수(타입 무관): ").append(totalCount).append("**\n");
        report.append("- numOfRows=100 기준 예상 페이지 수: ").append(pageCount(totalCount, 100)).append("\n\n");

        report.append("## 콘텐츠 타입별 건수\n\n");
        report.append("| contentTypeId | 타입 | totalCount |\n|---|---|---|\n");

        int sum = 0;
        for (Map.Entry<Integer, String> contentType : CONTENT_TYPES.entrySet()) {
            JsonNode response = call(() -> tourApiClient.areaBasedList(contentType.getKey(), 1, 1));
            int count = totalCountOf(response);
            sum += count;
            report.append("| ").append(contentType.getKey())
                    .append(" | ").append(contentType.getValue())
                    .append(" | ").append(count)
                    .append(" |\n");
        }
        report.append("| | **합계** | **").append(sum).append("** |\n\n");
        report.append("> 77(교통)이 0이면 KorService2에 해당 타입이 없다는 뜻이다. P5와 함께 판단한다.\n");

        write("p1-p3-scale.md", report.toString());
        System.out.println(report);
    }

    // ------------------------------------------------------------------
    // P4
    // ------------------------------------------------------------------

    @Test
    @DisplayName("P4 — detailPetTour2 벌크 응답 구조를 확인한다")
    void 펫정보_벌크_응답_구조를_확인한다() throws IOException {
        String rawBody = tourApiClient.detailPetTour(null, 1, 3);
        write("p4-bulk-sample.json", rawBody);

        JsonNode response = objectMapper.readTree(rawBody);
        List<JsonNode> items = itemsOf(response);

        StringBuilder report = new StringBuilder();
        report.append("# P4 — detailPetTour2 벌크 응답 구조\n\n");
        report.append("- totalCount: ").append(totalCountOf(response)).append("\n");
        report.append("- 반환된 item 수: ").append(items.size()).append("\n\n");

        if (items.isEmpty()) {
            report.append("**item이 비어 있다. contentId 생략 벌크 조회가 동작하지 않는 것으로 보인다.**\n");
            report.append("집합 연산 설계가 성립하지 않으므로 적재 방식을 다시 정해야 한다.\n");
        } else {
            JsonNode first = items.get(0);
            report.append("## 첫 item의 필드 목록\n\n");
            first.fieldNames().forEachRemaining(name ->
                    report.append("- `").append(name).append("` = ")
                            .append(abbreviate(first.path(name).asText(), 60)).append("\n"));

            boolean hasContentId = first.has("contentid");
            report.append("\n**contentid 포함 여부: ").append(hasContentId ? "있음" : "없음").append("**\n");
            if (!hasContentId) {
                report.append("\n> contentid가 없으면 집합 연산이 불가능하다. 설계 변경이 필요하다.\n");
            }
        }

        write("p4-bulk-structure.md", report.toString());
        System.out.println(report);
    }

    // ------------------------------------------------------------------
    // P5 · P7
    // ------------------------------------------------------------------

    @Test
    @DisplayName("P5·P7 — 펫 정보 전량을 수집해 조건 원문 분포를 집계한다")
    void 펫정보_전량을_수집해_분포를_집계한다() throws IOException {
        int numOfRows = 100;
        JsonNode firstPage = call(() -> tourApiClient.detailPetTour(null, 1, numOfRows));
        int totalCount = totalCountOf(firstPage);
        int pageCount = pageCount(totalCount, numOfRows);

        List<JsonNode> allItems = new ArrayList<>(itemsOf(firstPage));
        for (int page = 2; page <= pageCount; page++) {
            int currentPage = page;
            allItems.addAll(itemsOf(call(() -> tourApiClient.detailPetTour(null, currentPage, numOfRows))));
        }

        writeJsonLines("pet-tour-raw.jsonl", allItems);

        StringBuilder report = new StringBuilder();
        report.append("# P5·P7 — 펫 정보 집합(B) 분포\n\n");
        report.append("- totalCount: ").append(totalCount).append("\n");
        report.append("- 수집된 item 수: ").append(allItems.size()).append("\n");
        report.append("- 호출 횟수: ").append(pageCount).append("\n\n");

        appendPendingRatio(report, allItems);
        appendContentTypeDistribution(report, allItems);
        appendDistinctDistribution(report, allItems, "acmpyNeedMtr");
        appendDistinctDistribution(report, allItems, "acmpyTypeCd");
        appendBlankExpressions(report, allItems);
        appendMarkupContamination(report, allItems);
        appendWeightPatterns(report, allItems);

        write("p5-p7-distribution.md", report.toString());
        System.out.println(report);
    }

    /** P1 — 조건 4필드가 전부 빈 시설의 비율. petAllowed가 PENDING이 되는 쪽이다. */
    private void appendPendingRatio(
            StringBuilder report,
            List<JsonNode> items
    ) {
        long blankCount = items.stream().filter(this::isConditionBlank).count();
        double ratio = items.isEmpty() ? 0 : (blankCount * 100.0 / items.size());

        report.append("## 조건 4필드 결측 (B 안에서의 PENDING)\n\n");
        report.append("- 전부 빈 item: ").append(blankCount)
                .append(" / ").append(items.size())
                .append(String.format(" (%.1f%%)%n%n", ratio));
    }

    private void appendContentTypeDistribution(
            StringBuilder report,
            List<JsonNode> items
    ) {
        Map<String, Integer> counts = new TreeMap<>();
        for (JsonNode item : items) {
            counts.merge(item.path("contenttypeid").asText("(없음)"), 1, Integer::sum);
        }

        report.append("## contentTypeId 분포 (P5 판단용)\n\n");
        report.append("| contentTypeId | 타입 | 건수 |\n|---|---|---|\n");
        counts.forEach((code, count) -> {
            String name = CONTENT_TYPES.getOrDefault(parseIntOrNull(code), "(알 수 없음)");
            report.append("| ").append(code).append(" | ").append(name)
                    .append(" | ").append(count).append(" |\n");
        });
        report.append("\n> A(areaBasedList2)에 없는 타입이 여기 있으면 `B ⊄ A`다.\n")
                .append("> 그 경우 적재 대상을 `A ∪ B`로 넓히고 B-only는 detailCommon2로 기본정보를 보충해야 한다.\n\n");
    }

    /**
     * P7 — 파싱 방식을 가르는 핵심 집계.
     * 상위 30개가 90% 이상을 덮으면 고유값 사전 매핑으로 충분하고, 파편화되면 AI 파싱이 필요하다.
     */
    private void appendDistinctDistribution(
            StringBuilder report,
            List<JsonNode> items,
            String fieldName
    ) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JsonNode item : items) {
            String value = normalize(item.path(fieldName).asText(""));
            if (!value.isEmpty()) {
                counts.merge(value, 1, Integer::sum);
            }
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());

        int filled = counts.values().stream().mapToInt(Integer::intValue).sum();
        int top30 = sorted.stream().limit(30).mapToInt(Map.Entry::getValue).sum();
        double coverage = filled == 0 ? 0 : (top30 * 100.0 / filled);

        report.append("## `").append(fieldName).append("` 고유값 분포\n\n");
        report.append("- 값이 있는 item: ").append(filled).append("\n");
        report.append("- 고유값 수: ").append(counts.size()).append("\n");
        report.append(String.format("- **상위 30개 누적 커버리지: %.1f%%**%n", coverage));
        report.append(coverage >= 90
                ? "- → 고유값 사전 매핑으로 충분하다. AI 파싱 불필요.\n\n"
                : "- → 표현이 파편화되어 있다. AI 파싱 또는 하이브리드를 검토한다.\n\n");

        report.append("| 값 | 건수 |\n|---|---|\n");
        sorted.stream().limit(30).forEach(entry ->
                report.append("| ").append(abbreviate(entry.getKey(), 80))
                        .append(" | ").append(entry.getValue()).append(" |\n"));
        report.append("\n");
    }

    /** 정규화 목록(BLANK_EXPRESSIONS)을 확정하기 위한 실태 조사. */
    private void appendBlankExpressions(
            StringBuilder report,
            List<JsonNode> items
    ) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JsonNode item : items) {
            for (String field : CONDITION_FIELDS) {
                String raw = item.path(field).asText("");
                String trimmed = raw.trim();
                if (!trimmed.isEmpty() && trimmed.length() <= 6) {
                    counts.merge(trimmed, 1, Integer::sum);
                }
            }
        }

        report.append("## 짧은 값 (빈 값 표현 후보)\n\n");
        report.append("| 값 | 건수 | 현재 BLANK 목록 포함 |\n|---|---|---|\n");
        counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .limit(30)
                .forEach(entry -> report.append("| `").append(entry.getKey())
                        .append("` | ").append(entry.getValue())
                        .append(" | ").append(BLANK_EXPRESSIONS.contains(entry.getKey()) ? "O" : "")
                        .append(" |\n"));
        report.append("\n");
    }

    /** JSoup 등 HTML 파서 도입이 필요한지 판단한다. */
    private void appendMarkupContamination(
            StringBuilder report,
            List<JsonNode> items
    ) {
        long tagCount = 0;
        long entityCount = 0;
        for (JsonNode item : items) {
            for (String field : CONDITION_FIELDS) {
                String raw = item.path(field).asText("");
                if (raw.contains("<")) {
                    tagCount++;
                }
                if (raw.contains("&")) {
                    entityCount++;
                }
            }
        }

        report.append("## HTML 혼입\n\n");
        report.append("- `<` 포함 필드: ").append(tagCount).append("\n");
        report.append("- `&` 포함 필드: ").append(entityCount).append("\n");
        report.append(tagCount + entityCount > 0
                ? "- → 정규화 파이프라인에 태그 제거·엔티티 디코딩이 필요하다.\n\n"
                : "- → 단순 trim으로 충분하다. JSoup 도입 불필요.\n\n");
    }

    /** maxWeight 추출 정규식을 설계하기 위한 실제 표현 수집. */
    private void appendWeightPatterns(
            StringBuilder report,
            List<JsonNode> items
    ) {
        List<String> samples = new ArrayList<>();
        for (JsonNode item : items) {
            String text = normalize(item.path("acmpyPsblCpam").asText(""));
            Matcher matcher = WEIGHT_PATTERN.matcher(text);
            if (matcher.find()) {
                samples.add(text);
            }
        }

        report.append("## 체중 표현 (maxWeight 추출 대상)\n\n");
        report.append("- 체중 언급이 있는 item: ").append(samples.size())
                .append(" / ").append(items.size()).append("\n\n");
        report.append("| 원문 |\n|---|\n");
        samples.stream().distinct().limit(40)
                .forEach(sample -> report.append("| ").append(abbreviate(sample, 100)).append(" |\n"));
        report.append("\n> `이하·미만·까지·초과 불가`만 상한으로 본다. `이상·초과`는 무시해야 한다.\n\n");
    }

    // ------------------------------------------------------------------
    // P6
    // ------------------------------------------------------------------

    @Test
    @DisplayName("P6 — 음식점(39)의 분류체계 분포로 CAFE/RESTAURANT 분기 기준을 찾는다")
    void 음식점_분류체계_분포를_확인한다() throws IOException {
        write("p6-lcls-code.json", tourApiClient.lclsSystmCode(true, 1, 500));

        int numOfRows = 100;
        JsonNode firstPage = call(() -> tourApiClient.areaBasedList(39, 1, numOfRows));
        int totalCount = totalCountOf(firstPage);
        int sampledPages = Math.min(pageCount(totalCount, numOfRows), 5);

        List<JsonNode> items = new ArrayList<>(itemsOf(firstPage));
        for (int page = 2; page <= sampledPages; page++) {
            int currentPage = page;
            items.addAll(itemsOf(call(() -> tourApiClient.areaBasedList(39, currentPage, numOfRows))));
        }

        Map<String, Integer> counts = new TreeMap<>();
        for (JsonNode item : items) {
            String key = item.path("lclsSystm1").asText("") + " / "
                    + item.path("lclsSystm2").asText("") + " / "
                    + item.path("lclsSystm3").asText("");
            counts.merge(key, 1, Integer::sum);
        }

        StringBuilder report = new StringBuilder();
        report.append("# P6 — 음식점(39)의 lclsSystm 분포\n\n");
        report.append("- 음식점 전체: ").append(totalCount).append("\n");
        report.append("- 표본: ").append(items.size()).append(" (").append(sampledPages).append("페이지)\n\n");
        report.append("| lclsSystm1 / 2 / 3 | 건수 |\n|---|---|\n");
        counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .forEach(entry -> report.append("| `").append(entry.getKey())
                        .append("` | ").append(entry.getValue()).append(" |\n"));
        report.append("\n> p6-lcls-code.json에서 각 코드의 이름을 찾아 CAFE에 해당하는 코드를 확정한다.\n");

        write("p6-food-category.md", report.toString());
        System.out.println(report);
    }

    // ------------------------------------------------------------------
    // P8
    // ------------------------------------------------------------------

    /** 한 번에 받기를 시험할 numOfRows 계단. 문서에 상한이 없어 실측으로 찾는다. */
    private static final List<Integer> PAGE_SIZE_STEPS = List.of(100, 500, 1000, 5000, 10000, 20000);

    /** 적재 대상 콘텐츠 타입. 25(여행코스)와 77(교통)은 시설이 아니라 제외한다. */
    private static final List<Integer> LOADED_CONTENT_TYPES = List.of(12, 14, 15, 28, 32, 38, 39);

    /**
     * 클라이언트가 호출 사이에 두는 최소 간격이 측정치에 섞이지 않도록 미리 비워둘 시간.
     *
     * <p>{@code TourApiClient}는 직전 호출로부터 250ms가 지나지 않았으면 그만큼 잠들고 나서 요청을
     * 보낸다. 그 대기가 응답 시간으로 잡히면 "한 번에 받는 데 얼마나 걸리나"를 재는 의미가 없어진다.
     */
    private static final long SETTLE_MILLIS = 300L;

    @Test
    @DisplayName("P8 — 지역·카테고리 조회를 한 번에 받을 수 있는지(numOfRows 상한·조합별 규모·응답 비용) 확인한다")
    void 지역_카테고리_조회의_한계를_확인한다() throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# P8 — 지역·카테고리 조회를 한 번에 받을 수 있는가\n\n");
        report.append("> 전체 시설 목록 API를 관광공사 실시간 호출로 구현할 때, 조건별 전량을 한 응답에\n");
        report.append("> 받아 서버에서 필터·페이징할 수 있는지 판단하기 위한 측정이다.\n\n");

        appendPageSizeLimit(report);
        WidestRegion widest = appendRegionScale(report);
        appendResponseCost(report, widest);

        write("p8-region-category-scale.md", report.toString());
        System.out.println(report);
    }

    /**
     * numOfRows를 키워가며 어디까지 한 응답에 담겨 오는지 본다.
     *
     * <p>요청한 수보다 적게 오기 시작하는 지점이 상한이다. 총 건수에 도달해서 적게 온 것과는
     * 구분해야 하므로 totalCount를 함께 적는다.
     */
    private void appendPageSizeLimit(StringBuilder report) {
        report.append("## 1. numOfRows 상한 — 음식점(39) 전국\n\n");
        report.append("| 요청 numOfRows | 받은 item | totalCount | 응답 크기 | 소요 | 결과 |\n");
        report.append("|---|---|---|---|---|---|\n");

        for (Integer numOfRows : PAGE_SIZE_STEPS) {
            Measurement measurement = measure(
                    () -> tourApiClient.areaBasedList(39, null, null, 1, numOfRows));

            report.append("| ").append(numOfRows)
                    .append(" | ").append(measurement.itemCountText(this))
                    .append(" | ").append(measurement.totalCountText(this))
                    .append(" | ").append(measurement.sizeText())
                    .append(" | ").append(measurement.elapsedMillis()).append("ms")
                    .append(" | ").append(measurement.resultText())
                    .append(" |\n");
        }

        report.append("\n> 받은 item이 요청값보다 적은데 totalCount는 그보다 크면 거기가 상한이다.\n\n");
    }

    /**
     * 지역으로 좁혔을 때의 규모를 잰다.
     *
     * <p>먼저 시도별 건수의 합이 전국 건수와 맞는지 본다. 어긋나면 {@code lDongRegnCd}가 무시되고
     * 있다는 뜻이라 이 방식 자체가 성립하지 않는다.
     *
     * @return 가장 큰 시도와 그 안에서 가장 큰 시군구. 응답 비용의 최악 케이스다
     */
    private WidestRegion appendRegionScale(StringBuilder report) throws IOException {
        List<JsonNode> regionItems = itemsOf(call(() -> tourApiClient.ldongCode(null, true, 1, 500)));

        Map<String, String> sidoNames = new LinkedHashMap<>();
        for (JsonNode regionItem : regionItems) {
            String sidoCode = regionItem.path("lDongRegnCd").asText("");
            if (!sidoCode.isEmpty()) {
                sidoNames.putIfAbsent(sidoCode, regionItem.path("lDongRegnNm").asText(""));
            }
        }

        int nationwideCount = totalCountOf(call(() -> tourApiClient.areaBasedList(null, 1, 1)));

        Map<String, Integer> sidoCounts = new LinkedHashMap<>();
        for (String sidoCode : sidoNames.keySet()) {
            sidoCounts.put(sidoCode, totalCountOf(
                    call(() -> tourApiClient.areaBasedList(null, sidoCode, null, 1, 1))));
        }

        int sidoSum = sidoCounts.values().stream().mapToInt(Integer::intValue).sum();

        report.append("## 2. 지역 필터 유효성과 시도별 규모\n\n");
        report.append("- 전국 totalCount: **").append(nationwideCount).append("**\n");
        report.append("- 시도별 totalCount 합: **").append(sidoSum).append("**")
                .append(sidoSum == nationwideCount ? " (일치)" : " (불일치)").append("\n");
        report.append("- 판정: ").append(sidoSum == nationwideCount
                        ? "`lDongRegnCd`가 실제로 걸린다."
                        : "합이 어긋난다. 무시되는지, 지역 미분류 시설이 있는지 확인이 필요하다.")
                .append("\n\n");

        report.append("| 시도 | 코드 | totalCount |\n|---|---|---|\n");
        List<Map.Entry<String, Integer>> sortedSido = new ArrayList<>(sidoCounts.entrySet());
        sortedSido.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());
        for (Map.Entry<String, Integer> entry : sortedSido) {
            report.append("| ").append(sidoNames.get(entry.getKey()))
                    .append(" | `").append(entry.getKey())
                    .append("` | ").append(entry.getValue()).append(" |\n");
        }

        String widestSidoCode = sortedSido.get(0).getKey();
        String widestSidoName = sidoNames.get(widestSidoCode);
        int widestSidoCount = sortedSido.get(0).getValue();

        appendCategoryScale(report, widestSidoCode, widestSidoName);

        return appendSigunguScale(report, regionItems, widestSidoCode, widestSidoName, widestSidoCount);
    }

    /** 가장 큰 시도 안에서 카테고리를 걸면 얼마나 줄어드는지 본다. */
    private void appendCategoryScale(
            StringBuilder report,
            String sidoCode,
            String sidoName
    ) throws IOException {
        report.append("\n### 2-1. 가장 큰 시도(").append(sidoName).append(")의 카테고리별 규모\n\n");
        report.append("| contentTypeId | 타입 | totalCount |\n|---|---|---|\n");

        for (Integer contentTypeId : LOADED_CONTENT_TYPES) {
            int count = totalCountOf(
                    call(() -> tourApiClient.areaBasedList(contentTypeId, sidoCode, null, 1, 1)));
            report.append("| ").append(contentTypeId)
                    .append(" | ").append(CONTENT_TYPES.get(contentTypeId))
                    .append(" | ").append(count).append(" |\n");
        }
    }

    /** 시군구까지 좁히면 얼마나 남는지 본다. 실제 화면의 지역 칩이 이 단위다. */
    private WidestRegion appendSigunguScale(
            StringBuilder report,
            List<JsonNode> regionItems,
            String sidoCode,
            String sidoName,
            int sidoCount
    ) throws IOException {
        Map<String, String> sigunguNames = new LinkedHashMap<>();
        for (JsonNode regionItem : regionItems) {
            if (!sidoCode.equals(regionItem.path("lDongRegnCd").asText(""))) {
                continue;
            }
            String sigunguCode = regionItem.path("lDongSignguCd").asText("");
            if (!sigunguCode.isEmpty()) {
                sigunguNames.putIfAbsent(sigunguCode, regionItem.path("lDongSignguNm").asText(""));
            }
        }

        report.append("\n### 2-2. 가장 큰 시도(").append(sidoName).append(")의 시군구별 규모\n\n");
        report.append("| 시군구 | 코드 | totalCount |\n|---|---|---|\n");

        Map<String, Integer> sigunguCounts = new LinkedHashMap<>();
        for (String sigunguCode : sigunguNames.keySet()) {
            sigunguCounts.put(sigunguCode, totalCountOf(
                    call(() -> tourApiClient.areaBasedList(null, sidoCode, sigunguCode, 1, 1))));
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(sigunguCounts.entrySet());
        sorted.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());
        for (Map.Entry<String, Integer> entry : sorted) {
            report.append("| ").append(sigunguNames.get(entry.getKey()))
                    .append(" | `").append(entry.getKey())
                    .append("` | ").append(entry.getValue()).append(" |\n");
        }

        if (sorted.isEmpty()) {
            return new WidestRegion(sidoCode, sidoName, sidoCount, null, null, 0);
        }

        Map.Entry<String, Integer> widest = sorted.get(0);
        return new WidestRegion(
                sidoCode,
                sidoName,
                sidoCount,
                widest.getKey(),
                sigunguNames.get(widest.getKey()),
                widest.getValue()
        );
    }

    /**
     * 최악 조합을 실제로 한 번에 받아 응답 크기와 왕복 시간을 잰다.
     *
     * <p>시도 전체(카테고리 없음)가 지역을 지정한 요청 중 가장 무거운 경우다. 시군구는 그보다
     * 항상 작으므로 화면에서 실제로 밟게 될 비용의 위아래를 함께 보게 된다.
     */
    private void appendResponseCost(
            StringBuilder report,
            WidestRegion widest
    ) {
        report.append("\n## 3. 한 번에 받을 때의 응답 비용\n\n");
        report.append("| 조건 | 요청 numOfRows | 받은 item | 응답 크기 | 소요 | 결과 |\n");
        report.append("|---|---|---|---|---|---|\n");

        appendCostRow(report, widest.sidoName() + " 전체", widest.facilityCount(),
                () -> tourApiClient.areaBasedList(
                        null, widest.sidoCode(), null, 1, Math.max(widest.facilityCount(), 1)));

        if (widest.sigunguCode() != null) {
            appendCostRow(report, widest.sidoName() + " " + widest.sigunguName(), widest.sigunguFacilityCount(),
                    () -> tourApiClient.areaBasedList(
                            null, widest.sidoCode(), widest.sigunguCode(), 1,
                            Math.max(widest.sigunguFacilityCount(), 1)));
        }

        report.append("\n> 소요는 호출 간 최소 간격을 빼고 잰 순수 왕복+수신 시간이다. 파싱과 DB 조회는 별도다.\n");
    }

    private void appendCostRow(
            StringBuilder report,
            String label,
            int requestedRows,
            ResponseSupplier supplier
    ) {
        Measurement measurement = measure(supplier);
        report.append("| ").append(label)
                .append(" | ").append(requestedRows)
                .append(" | ").append(measurement.itemCountText(this))
                .append(" | ").append(measurement.sizeText())
                .append(" | ").append(measurement.elapsedMillis()).append("ms")
                .append(" | ").append(measurement.resultText())
                .append(" |\n");
    }

    /**
     * 한 번 호출하고 응답 크기와 걸린 시간을 남긴다.
     *
     * <p>실패도 측정 결과다. 상한을 넘기면 예외가 나는지 잘려서 오는지가 판단 재료라, 던지지 않고
     * 사유를 표에 적는다.
     */
    private Measurement measure(ResponseSupplier supplier) {
        settle();

        long startedAtNanos = System.nanoTime();
        try {
            String body = supplier.get();
            return new Measurement(body, elapsedMillisSince(startedAtNanos), null);
        } catch (RuntimeException exception) {
            return new Measurement(null, elapsedMillisSince(startedAtNanos), exception.getMessage());
        }
    }

    private void settle() {
        try {
            Thread.sleep(SETTLE_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private long elapsedMillisSince(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private JsonNode parseOrNull(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            return null;
        }
    }

    /** 가장 큰 시도와 그 안에서 가장 큰 시군구. 응답 비용의 최악 케이스다. */
    private record WidestRegion(
            String sidoCode,
            String sidoName,
            int facilityCount,
            String sigunguCode,
            String sigunguName,
            int sigunguFacilityCount
    ) {
    }

    /** 호출 한 번의 측정 결과. {@code failure}가 있으면 응답을 받지 못한 것이다. */
    private record Measurement(
            String body,
            long elapsedMillis,
            String failure
    ) {

        String itemCountText(TourApiProbeTest probe) {
            if (body == null) {
                return "-";
            }
            JsonNode response = probe.parseOrNull(body);
            return response == null ? "파싱 실패" : String.valueOf(probe.itemsOf(response).size());
        }

        String totalCountText(TourApiProbeTest probe) {
            if (body == null) {
                return "-";
            }
            JsonNode response = probe.parseOrNull(body);
            return response == null ? "-" : String.valueOf(probe.totalCountOf(response));
        }

        String sizeText() {
            if (body == null) {
                return "-";
            }
            int bytes = body.getBytes(StandardCharsets.UTF_8).length;
            if (bytes < 1024) {
                return bytes + "B";
            }
            if (bytes < 1024 * 1024) {
                return (bytes / 1024) + "KB";
            }
            return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
        }

        String resultText() {
            return failure == null ? "성공" : "실패 — " + failure.replace("|", "\\|");
        }
    }

    // ------------------------------------------------------------------
    // P9
    // ------------------------------------------------------------------

    /** 음식점(39) 아래 분류체계 중분류 후보. P6 표본에 나온 것 + 빈 코드까지 훑는다. */
    private static final List<String> FOOD_MEDIUM_CATEGORIES =
            List.of("FD01", "FD02", "FD03", "FD04", "FD05", "FD06");

    /** 카페·찻집. 이 코드만 CAFE로 가르고 나머지는 RESTAURANT다. */
    private static final String MEDIUM_CATEGORY_CAFE = "FD05";

    /** 지역과 함께 걸리는지 확인할 때 쓸 시도. 가장 큰 경기도다. */
    private static final String SIDO_CODE_GYEONGGI = "41";

    @Test
    @DisplayName("P9 — lclsSystm2가 요청 파라미터로 걸리는지(CAFE/RESTAURANT 분기 가능 여부) 확인한다")
    void 분류체계_중분류_필터가_걸리는지_확인한다() throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# P9 — lclsSystm2 요청 필터 유효성\n\n");
        report.append("> 카페와 음식점은 둘 다 `contentTypeId=39`라, 관광공사 쪽에서 가르려면\n");
        report.append("> 중분류를 요청 파라미터로 받아줘야 한다.\n\n");

        int foodTotalCount = totalCountOf(call(() -> tourApiClient.areaBasedList(39, 1, 1)));
        report.append("- 음식점(39) 전국 totalCount: **").append(foodTotalCount).append("**\n\n");

        report.append("## 중분류별 totalCount\n\n");
        report.append("| lclsSystm2 | totalCount |\n|---|---|\n");

        int mediumCategorySum = 0;
        for (String mediumCategoryCode : FOOD_MEDIUM_CATEGORIES) {
            int count = totalCountOf(call(() -> tourApiClient.areaBasedList(
                    39, null, null, mediumCategoryCode, 1, 1)));
            mediumCategorySum += count;
            report.append("| `").append(mediumCategoryCode).append("` | ").append(count).append(" |\n");
        }

        report.append("| **합** | **").append(mediumCategorySum).append("** |\n\n");
        report.append("- 판정: ").append(verdictOf(foodTotalCount, mediumCategorySum)).append("\n\n");

        appendCafeSampleCheck(report);
        appendRegionCombinationCheck(report, foodTotalCount);

        write("p9-lcls-filter.md", report.toString());
        System.out.println(report);
    }

    /**
     * 중분류 합과 음식점 전체를 비교해 필터가 먹는지 판정한다.
     *
     * <p>파라미터가 무시되면 중분류마다 음식점 전체 건수가 그대로 돌아와 합이 몇 배로 뛴다.
     */
    private String verdictOf(
            int foodTotalCount,
            int mediumCategorySum
    ) {
        if (mediumCategorySum >= foodTotalCount * 2) {
            return "**무시된다.** 중분류마다 전체 건수가 그대로 돌아왔다. CAFE/RESTAURANT는 관광공사에서 못 가른다.";
        }
        if (mediumCategorySum == 0) {
            return "**거부된다.** 어떤 중분류도 결과를 내지 못했다. 코드 체계나 파라미터 이름을 다시 봐야 한다.";
        }
        return "**걸린다.** 합이 음식점 전체보다 작거나 같다 (차이 "
                + (foodTotalCount - mediumCategorySum) + "건은 중분류가 비어 있는 시설로 본다).";
    }

    /** 카페 코드로 받은 응답이 정말 그 코드만 담고 있는지 표본으로 확인한다. */
    private void appendCafeSampleCheck(StringBuilder report) throws IOException {
        List<JsonNode> sample = itemsOf(call(() -> tourApiClient.areaBasedList(
                39, null, null, MEDIUM_CATEGORY_CAFE, 1, 100)));

        Map<String, Integer> counts = new TreeMap<>();
        for (JsonNode item : sample) {
            counts.merge(item.path("lclsSystm2").asText(""), 1, Integer::sum);
        }

        report.append("## `FD05` 응답 표본의 실제 lclsSystm2 (100건)\n\n");
        report.append("| 응답의 lclsSystm2 | 건수 |\n|---|---|\n");
        counts.forEach((code, count) ->
                report.append("| `").append(code).append("` | ").append(count).append(" |\n"));

        boolean isCafeOnly = counts.size() == 1 && counts.containsKey(MEDIUM_CATEGORY_CAFE);
        report.append("\n- 판정: ").append(isCafeOnly
                ? "요청한 코드만 내려온다."
                : "요청하지 않은 코드가 섞여 있다. 서버에서 다시 걸러야 한다.").append("\n\n");
    }

    /** 지역 필터와 중분류를 함께 걸어도 둘 다 유지되는지 본다. 실제 조회는 늘 함께 건다. */
    private void appendRegionCombinationCheck(
            StringBuilder report,
            int foodTotalCount
    ) throws IOException {
        int cafeNationwide = totalCountOf(call(() -> tourApiClient.areaBasedList(
                39, null, null, MEDIUM_CATEGORY_CAFE, 1, 1)));
        int foodInSido = totalCountOf(call(() -> tourApiClient.areaBasedList(
                39, SIDO_CODE_GYEONGGI, null, 1, 1)));
        int cafeInSido = totalCountOf(call(() -> tourApiClient.areaBasedList(
                39, SIDO_CODE_GYEONGGI, null, MEDIUM_CATEGORY_CAFE, 1, 1)));

        report.append("## 지역 + 중분류 동시 적용 (경기도, 카페)\n\n");
        report.append("| 조건 | totalCount |\n|---|---|\n");
        report.append("| 음식점 전국 | ").append(foodTotalCount).append(" |\n");
        report.append("| 음식점 + 카페(전국) | ").append(cafeNationwide).append(" |\n");
        report.append("| 음식점 + 경기도 | ").append(foodInSido).append(" |\n");
        report.append("| 음식점 + 경기도 + 카페 | ").append(cafeInSido).append(" |\n\n");

        boolean isBothApplied = cafeInSido < cafeNationwide && cafeInSido < foodInSido;
        report.append("- 판정: ").append(isBothApplied
                ? "두 필터가 함께 걸린다."
                : "한쪽이 무시된다. 조합 조건은 서버에서 걸러야 한다.").append("\n");
    }

    // ------------------------------------------------------------------
    // 공통 유틸
    // ------------------------------------------------------------------

    private static final String SERVICE_KEY_PROPERTY = "tour-api.service-key";
    private static final String SERVICE_KEY_ENVIRONMENT = "TOUR_API_SERVICE_KEY";

    /**
     * 인증키를 찾는다. 환경변수가 우선이고, 없으면 {@code application.yml}에서 읽는다.
     *
     * <p>yml은 gitignore 대상이라 키가 저장소에 올라가지 않는다.
     */
    private static String serviceKey() {
        String fromEnvironment = System.getenv(SERVICE_KEY_ENVIRONMENT);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }

        String fromYaml = readServiceKeyFromApplicationYaml();
        if (fromYaml != null && !fromYaml.isBlank()) {
            return fromYaml;
        }

        throw new IllegalStateException(
                "인증키를 찾을 수 없습니다. application.yml에 아래를 추가하거나 환경변수 "
                        + SERVICE_KEY_ENVIRONMENT + "를 설정하세요.\n\n"
                        + "tour-api:\n"
                        + "  service-key: 발급받은키\n\n"
                        + "공공데이터포털 '한국관광공사_국문 관광정보 서비스(KorService2)' 인증키여야 합니다."
        );
    }

    private static String readServiceKeyFromApplicationYaml() {
        try {
            ClassPathResource resource = new ClassPathResource("application.yml");
            if (!resource.exists()) {
                return null;
            }

            List<PropertySource<?>> sources =
                    new YamlPropertySourceLoader().load("application", resource);

            return sources.stream()
                    .map(source -> source.getProperty(SERVICE_KEY_PROPERTY))
                    .filter(java.util.Objects::nonNull)
                    .map(Object::toString)
                    .findFirst()
                    .orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

    private JsonNode call(ResponseSupplier supplier) throws IOException {
        return objectMapper.readTree(supplier.get());
    }

    @FunctionalInterface
    private interface ResponseSupplier {
        String get();
    }

    /** items가 빈 문자열로 오거나 item이 배열이 아닌 객체로 오는 경우를 모두 흡수한다. */
    private List<JsonNode> itemsOf(JsonNode response) {
        JsonNode items = response.path("response").path("body").path("items");
        if (!items.isObject()) {
            return List.of();
        }

        JsonNode item = items.path("item");
        if (item.isArray()) {
            List<JsonNode> result = new ArrayList<>();
            item.forEach(result::add);
            return result;
        }
        if (item.isObject()) {
            return List.of(item);
        }
        return List.of();
    }

    private int totalCountOf(JsonNode response) {
        return response.path("response").path("body").path("totalCount").asInt(0);
    }

    private boolean isConditionBlank(JsonNode item) {
        return CONDITION_FIELDS.stream()
                .allMatch(field -> normalize(item.path(field).asText("")).isEmpty());
    }

    private String normalize(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String stripped = rawValue
                .replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
        return BLANK_EXPRESSIONS.contains(stripped) ? "" : stripped;
    }

    private int pageCount(
            int totalCount,
            int numOfRows
    ) {
        return (totalCount + numOfRows - 1) / numOfRows;
    }

    private Integer parseIntOrNull(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String abbreviate(
            String value,
            int maxLength
    ) {
        String single = value.replace("\n", " ").replace("|", "\\|");
        return single.length() <= maxLength ? single : single.substring(0, maxLength) + "…";
    }

    private void write(
            String fileName,
            String content
    ) throws IOException {
        Files.createDirectories(OUTPUT_DIRECTORY);
        Files.writeString(OUTPUT_DIRECTORY.resolve(fileName), content, StandardCharsets.UTF_8);
        System.out.println("→ " + OUTPUT_DIRECTORY.resolve(fileName).toAbsolutePath());
    }

    private void writeJsonLines(
            String fileName,
            List<JsonNode> items
    ) throws IOException {
        StringBuilder lines = new StringBuilder();
        for (JsonNode item : items) {
            lines.append(objectMapper.writeValueAsString(item)).append('\n');
        }
        write(fileName, lines.toString());
    }

}
