package com.freepets.domain.facility.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.entity.Region;
import com.freepets.domain.facility.repository.RegionRepository;
import com.freepets.infra.tourapi.RegionNameTable;
import com.freepets.infra.tourapi.TourApiClient;
import com.freepets.infra.tourapi.TourApiResponseParser;
import com.freepets.infra.tourapi.dto.LdongCodeItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관광공사 법정동 코드({@code ldongCode2})를 지역 테이블에 적재한다.
 *
 * <p>{@code lDongListYn=Y}로 호출하면 시도-시군구 매핑 전체가 한 번에 내려온다. 전국이 250여 건이라
 * 500건씩 한두 번이면 끝난다.
 *
 * <p>{@link FacilitySyncService}가 시설을 적재하기 전에 이걸 부른다. 시설 응답에는 지역이 코드로만
 * 담겨 오므로, 이름을 채우려면 매핑표가 먼저 있어야 한다. 예전에는 그 매핑표를 메모리에만 만들고
 * 버렸는데, 지역 칩을 그리려면 목록 자체가 필요해 테이블로 남긴다.
 *
 * <p>{@code FacilitySyncService}/{@code FacilityUpsertService}처럼 조회와 저장을 나누지 않았다.
 * 그 둘이 나뉜 이유는 수천 페이지를 도는 동안 트랜잭션을 페이지 단위로 끊기 위해서인데,
 * 지역은 호출이 한두 번이라 한 트랜잭션으로 끝내도 문제가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RegionSyncService {

    /** 관광공사가 허용하는 한 페이지 최대 건수. 전국이 250여 건이라 대개 한 번에 끝난다. */
    private static final int PAGE_SIZE = 500;

    private final TourApiClient tourApiClient;
    private final TourApiResponseParser tourApiResponseParser;
    private final RegionRepository regionRepository;

    /**
     * 지역을 전량 내려받아 테이블에 반영한다.
     *
     * @return 시설 적재가 코드를 이름으로 옮길 때 쓸 매핑표
     */
    public RegionNameTable syncRegions() {
        List<LdongCodeItem> items = fetchAll();
        save(items);

        log.info("지역을 적재했습니다. 응답 {}건", items.size());
        return new RegionNameTable(items);
    }

    private List<LdongCodeItem> fetchAll() {
        String firstPage = tourApiClient.ldongCode(null, true, 1, PAGE_SIZE);
        int totalCount = tourApiResponseParser.parseTotalCount(firstPage);

        List<LdongCodeItem> items = new ArrayList<>(
                tourApiResponseParser.parseItems(firstPage, LdongCodeItem.class));

        for (int page = 2; page <= pageCountOf(totalCount); page++) {
            items.addAll(tourApiResponseParser.parseItems(
                    tourApiClient.ldongCode(null, true, page, PAGE_SIZE), LdongCodeItem.class));
        }

        return items;
    }

    /**
     * 없으면 넣고, 이름이 바뀌었으면 고친다.
     *
     * <p>응답에 없는 기존 행은 <b>지우지 않는다.</b> 행정구역 폐지는 드문 일인데 반해, 관광공사 응답이
     * 일부만 내려온 상황에서 지우면 멀쩡한 지역이 통째로 사라진다. 경고만 남기고 사람이 판단하게 한다.
     */
    private void save(List<LdongCodeItem> items) {
        Map<String, Region> existing = findExisting();
        Set<String> seen = new HashSet<>();
        List<Region> toInsert = new ArrayList<>();

        for (LdongCodeItem item : items) {
            if (item.sidoCode() == null || item.sidoName() == null) {
                continue;
            }

            // 같은 지역이 응답에 두 번 담겨 와도 행이 두 개 생기지 않게 한다.
            String key = keyOf(item.sidoCode(), item.sigunguCode());
            if (!seen.add(key)) {
                continue;
            }

            Region region = existing.get(key);

            if (region == null) {
                toInsert.add(Region.builder()
                        .sidoCode(item.sidoCode())
                        .sido(item.sidoName())
                        .sigunguCode(item.sigunguCode())
                        .sigungu(item.sigunguName())
                        .build());
            } else if (isRenamed(region, item)) {
                region.updateNames(item.sidoName(), item.sigunguName());
            }
        }

        regionRepository.saveAll(toInsert);
        warnMissing(existing.keySet(), seen);

        log.info("지역 적재 결과 — 신규 {}건, 기존 {}건", toInsert.size(), existing.size());
    }

    private Map<String, Region> findExisting() {
        Map<String, Region> existing = new HashMap<>();

        for (Region region : regionRepository.findAll()) {
            existing.putIfAbsent(keyOf(region.getSidoCode(), region.getSigunguCode()), region);
        }

        return existing;
    }

    private boolean isRenamed(
            Region region,
            LdongCodeItem item
    ) {
        return !region.getSido().equals(item.sidoName())
                || !Objects.equals(region.getSigungu(), item.sigunguName());
    }

    private void warnMissing(
            Set<String> existingKeys,
            Set<String> fetchedKeys
    ) {
        List<String> missing = existingKeys.stream()
                .filter(key -> !fetchedKeys.contains(key))
                .toList();

        if (!missing.isEmpty()) {
            log.warn("관광공사 응답에 없는 지역이 {}건 남아 있습니다. 자동으로 지우지 않았습니다 — {}",
                    missing.size(), missing);
        }
    }

    /** 시군구가 없는 시도는 코드가 null이라 키에 빈자리로 들어간다. */
    private String keyOf(
            String sidoCode,
            String sigunguCode
    ) {
        return sidoCode + "-" + (sigunguCode == null ? "" : sigunguCode);
    }

    private int pageCountOf(int totalCount) {
        return (totalCount + PAGE_SIZE - 1) / PAGE_SIZE;
    }

}
