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
