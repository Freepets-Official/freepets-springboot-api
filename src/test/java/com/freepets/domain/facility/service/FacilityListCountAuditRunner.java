package com.freepets.domain.facility.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.freepets.domain.facility.dto.FacilityRequestDTO;
import com.freepets.domain.facility.dto.FacilityResponseDTO;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Region;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.repository.RegionRepository;

/**
 * 전체 시설 목록 조회를 시군구 전체에 돌려 건수를 실측한다.
 *
 * <p>화면에 뜬 전국 합계(전체 21,010 · 동반가능 6,456)가 실제 데이터(전체 48,743 · 동반가능 9,675)와
 * 맞지 않아, 그 차이가 어디서 생기는지 재려고 만들었다. 프론트가 시군구를 돌며 합산하는 것과 같은
 * 순서로 호출하고, 두 가지를 함께 센다.
 *
 * <ul>
 *   <li>{@code total}을 그대로 더한 값 — 올바르게 셌을 때 나와야 하는 수</li>
 *   <li>{@code min(total, 100)}을 더한 값 — 지역마다 한 페이지(최대 100건)만 받아
 *       목록 길이를 센 경우에 나오는 수</li>
 * </ul>
 *
 * <p>관광공사를 시군구 수만큼(250회 안팎) 부른다. 일일 호출 한도를 함께 쓰는 야간 적재가 있으므로
 * 아무 때나 돌리지 않는다.
 *
 * <pre>
 * ./gradlew facilityListAudit
 * </pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(
        named = "facility.list.audit",
        matches = "true",
        disabledReason = "실측 전용 실행기. ./gradlew facilityListAudit 로 실행한다."
)
class FacilityListCountAuditRunner {

    /** 프론트가 한 번에 받을 수 있는 최대 건수. 응답 DTO의 size 상한과 같다. */
    private static final int PAGE_SIZE_LIMIT = 100;

    /** 공공데이터포털에 대한 호출 예절. 조회용 클라이언트는 간격을 두지 않으므로 여기서 둔다. */
    private static final long INTERVAL_MILLIS = 200L;

    private static final Path OUTPUT_DIRECTORY = Path.of("build", "facility-list-audit");

    @Autowired
    private FacilityListQueryService facilityListQueryService;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private FacilityRepository facilityRepository;

    @Test
    @DisplayName("시군구를 모두 돌며 전체 시설 목록의 건수를 실측한다")
    void 시군구를_모두_돌며_전체_시설_목록의_건수를_실측한다() throws IOException {
        List<Region> regions = regionRepository.findAllByOrderBySidoCodeAscSigunguCodeAsc();

        List<Region> chips = regions.stream().filter(Region::hasSigungu).toList();
        long sidoOnlyCount = regions.size() - chips.size();

        Totals totals = new Totals();
        List<String> failures = new ArrayList<>();
        StringBuilder rows = new StringBuilder();

        for (int index = 0; index < chips.size(); index++) {
            Region region = chips.get(index);

            try {
                int total = totalOf(region);
                // 동반 가능 건수는 DB에서 센다. 관광공사 호출을 두 배로 늘리지 않기 위해서이고,
                // 어차피 필터 자체가 우리 DB의 판정값으로 걸린다.
                long allowed = facilityRepository.countAll(
                        null, PetAllowed.ALLOWED, region.getSidoCode(), region.getSigunguCode());

                totals.add(total, allowed);
                rows.append(row(region, total, allowed));
            } catch (RuntimeException exception) {
                failures.add(region.getSido() + " " + region.getSigungu() + " — " + exception.getMessage());
            }

            if ((index + 1) % 25 == 0) {
                System.out.printf("진행 %d/%d — 합계 %d%n", index + 1, chips.size(), totals.sumTotal);
            }
            sleep();
        }

        long databaseTotal = facilityRepository.countAll(null, null, null, null);
        long databaseAllowed = facilityRepository.countAll(null, PetAllowed.ALLOWED, null, null);

        String report = report(totals, chips.size(), sidoOnlyCount, databaseTotal, databaseAllowed, failures, rows);
        write(report);
        System.out.println(report);
    }

    private int totalOf(Region region) {
        FacilityRequestDTO.FacilityListRequest request = new FacilityRequestDTO.FacilityListRequest();
        request.setSidoCode(region.getSidoCode());
        request.setSigunguCode(region.getSigunguCode());
        request.setSize(1);

        FacilityResponseDTO.FacilityListResult result = facilityListQueryService.getFacilityList(request);
        return (int) result.total();
    }

    private String row(
            Region region,
            int total,
            long allowed
    ) {
        return "| " + region.getSido()
                + " | " + region.getSigungu()
                + " | " + total
                + " | " + Math.min(total, PAGE_SIZE_LIMIT)
                + " | " + allowed
                + " | " + Math.min(allowed, PAGE_SIZE_LIMIT)
                + " |\n";
    }

    private String report(
            Totals totals,
            int chipCount,
            long sidoOnlyCount,
            long databaseTotal,
            long databaseAllowed,
            List<String> failures,
            StringBuilder rows
    ) {
        StringBuilder report = new StringBuilder();
        report.append("# 전체 시설 목록 건수 실측\n\n");
        report.append("- 조회한 시군구: ").append(chipCount).append("곳");
        if (sidoOnlyCount > 0) {
            report.append(" (시군구가 없는 시도 ").append(sidoOnlyCount).append("곳은 제외 — 중복 집계 방지)");
        }
        report.append("\n");
        report.append("- 실패: ").append(failures.size()).append("건\n\n");

        report.append("## 합계\n\n");
        report.append("| 세는 방식 | 전체 | 동반 가능 |\n|---|---|---|\n");
        report.append("| `total`을 더함 (올바른 방식) | **").append(totals.sumTotal)
                .append("** | **").append(totals.sumAllowed).append("** |\n");
        report.append("| 지역마다 100건까지만 셈 `min(total, 100)` | **").append(totals.sumCappedTotal)
                .append("** | **").append(totals.sumCappedAllowed).append("** |\n");
        report.append("| DB 실제 건수 | ").append(databaseTotal)
                .append(" | ").append(databaseAllowed).append(" |\n\n");

        report.append("## 화면에 뜬 값과의 대조\n\n");
        report.append("| 값 | 화면 | `total` 합 | 100건 상한 합 |\n|---|---|---|---|\n");
        report.append("| 전체 | 21,010 | ").append(totals.sumTotal)
                .append(" | ").append(totals.sumCappedTotal).append(" |\n");
        report.append("| 동반 가능 | 6,456 | ").append(totals.sumAllowed)
                .append(" | ").append(totals.sumCappedAllowed).append(" |\n\n");

        if (!failures.isEmpty()) {
            report.append("## 실패한 지역\n\n");
            failures.forEach(failure -> report.append("- ").append(failure).append("\n"));
            report.append("\n");
        }

        report.append("## 지역별\n\n");
        report.append("| 시도 | 시군구 | 전체 | 전체(100 상한) | 동반가능 | 동반가능(100 상한) |\n");
        report.append("|---|---|---|---|---|---|\n");
        report.append(rows);

        return report.toString();
    }

    private void write(String report) throws IOException {
        Files.createDirectories(OUTPUT_DIRECTORY);
        Path path = OUTPUT_DIRECTORY.resolve("count-audit.md");
        Files.writeString(path, report, StandardCharsets.UTF_8);
        System.out.println("→ " + path.toAbsolutePath());
    }

    private void sleep() {
        try {
            Thread.sleep(INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** 네 가지 합계를 함께 센다. 세는 방식에 따라 얼마나 달라지는지가 이 실측의 목적이다. */
    private static final class Totals {

        private long sumTotal;
        private long sumCappedTotal;
        private long sumAllowed;
        private long sumCappedAllowed;

        private void add(
                int total,
                long allowed
        ) {
            sumTotal += total;
            sumCappedTotal += Math.min(total, PAGE_SIZE_LIMIT);
            sumAllowed += allowed;
            sumCappedAllowed += Math.min(allowed, PAGE_SIZE_LIMIT);
        }
    }

}
