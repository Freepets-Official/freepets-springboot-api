package com.freepets.domain.facility.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.infra.tourapi.RegionNameTable;
import com.freepets.infra.tourapi.TourApiClient;
import com.freepets.infra.tourapi.TourApiFacilityConverter;
import com.freepets.infra.tourapi.TourApiResponseParser;
import com.freepets.infra.tourapi.dto.AreaBasedItem;
import com.freepets.infra.tourapi.dto.LdongCodeItem;
import com.freepets.infra.tourapi.dto.PetTourItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관광공사 시설 데이터를 자체 DB에 적재한다.
 *
 * <p>두 집합의 차이로 동반 가능 여부를 정한다. 전체 시설 목록(A)을 훑으면서
 * 반려동물 동반 정보 목록(B)에 있는지 확인하는 방식이라, 시설마다 개별 호출을 하지 않는다.
 *
 * <pre>
 * contentId ∈ B → 조건 원문 파싱 → ALLOWED 또는 DENIED
 * contentId ∉ B → PENDING
 * </pre>
 *
 * <p>B를 먼저 메모리에 올린 뒤 A를 페이지 단위로 흘려보내며 처리한다. 시설이 저장되는 시점에
 * 판정이 이미 확정돼 있어, 배치가 중간에 끊겨도 들어간 데이터는 정합하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilitySyncService {

    private static final int PAGE_SIZE = 100;

    private final TourApiClient tourApiClient;
    private final TourApiResponseParser tourApiResponseParser;
    private final TourApiFacilityConverter tourApiFacilityConverter;
    private final FacilityUpsertService facilityUpsertService;

    /**
     * 전체 시설을 적재한다.
     *
     * <p>호출량은 {@code 지역코드 2회 + B÷100 + A÷100}이다.
     */
    public FacilitySyncResult syncAll() {
        RegionNameTable regionNameTable = loadRegionNameTable();
        Map<String, PetTourItem> petTourItems = loadPetTourItems();

        String firstPage = tourApiClient.areaBasedList(null, 1, PAGE_SIZE);
        int totalCount = tourApiResponseParser.parseTotalCount(firstPage);
        int lastPage = pageCountOf(totalCount);

        log.info("시설 적재를 시작합니다. 전체 {}건, {}페이지, 펫 정보 보유 {}건",
                totalCount, lastPage, petTourItems.size());

        FacilitySyncResult result = new FacilitySyncResult();
        savePage(tourApiResponseParser.parseItems(firstPage, AreaBasedItem.class),
                petTourItems, regionNameTable, result);

        for (int page = 2; page <= lastPage; page++) {
            String body = tourApiClient.areaBasedList(null, page, PAGE_SIZE);
            savePage(tourApiResponseParser.parseItems(body, AreaBasedItem.class),
                    petTourItems, regionNameTable, result);

            if (page % 50 == 0) {
                log.info("적재 진행 {}/{} 페이지 — {}", page, lastPage, result.summary());
            }
        }

        log.info("시설 적재를 마쳤습니다. {}", result.summary());
        return result;
    }

    /**
     * 반려동물 동반 정보를 전량 받아 {@code contentId}로 색인한다.
     *
     * <p>{@code contentId}를 생략해 호출하면 동반 정보를 보유한 전체 목록이 페이징으로 내려온다.
     * 시설마다 개별 호출할 필요가 없다.
     */
    private Map<String, PetTourItem> loadPetTourItems() {
        String firstPage = tourApiClient.detailPetTour(null, 1, PAGE_SIZE);
        int totalCount = tourApiResponseParser.parseTotalCount(firstPage);
        int lastPage = pageCountOf(totalCount);

        List<PetTourItem> items = new ArrayList<>(
                tourApiResponseParser.parseItems(firstPage, PetTourItem.class));

        for (int page = 2; page <= lastPage; page++) {
            String body = tourApiClient.detailPetTour(null, page, PAGE_SIZE);
            items.addAll(tourApiResponseParser.parseItems(body, PetTourItem.class));
        }

        return items.stream()
                .filter(item -> item.contentId() != null)
                .collect(Collectors.toMap(
                        PetTourItem::contentId,
                        Function.identity(),
                        (first, second) -> first));
    }

    private RegionNameTable loadRegionNameTable() {
        String body = tourApiClient.ldongCode(null, true, 1, 500);
        int totalCount = tourApiResponseParser.parseTotalCount(body);

        List<LdongCodeItem> items = new ArrayList<>(
                tourApiResponseParser.parseItems(body, LdongCodeItem.class));

        for (int page = 2; page <= pageCountOf(totalCount, 500); page++) {
            items.addAll(tourApiResponseParser.parseItems(
                    tourApiClient.ldongCode(null, true, page, 500), LdongCodeItem.class));
        }

        RegionNameTable regionNameTable = new RegionNameTable(items);
        log.info("지역 코드표를 불러왔습니다. 시군구 {}건", regionNameTable.size());
        return regionNameTable;
    }

    /**
     * 한 페이지를 판정해 저장한다. 저장이 끝나면 메모리에서 놓아준다.
     */
    private void savePage(
            List<AreaBasedItem> areaBasedItems,
            Map<String, PetTourItem> petTourItems,
            RegionNameTable regionNameTable,
            FacilitySyncResult result
    ) {
        List<Facility> converted = new ArrayList<>();

        for (AreaBasedItem areaBasedItem : areaBasedItems) {
            Facility facility = tourApiFacilityConverter.convert(
                    areaBasedItem,
                    petTourItems.get(areaBasedItem.contentId()),
                    regionNameTable
            );

            if (facility == null) {
                result.addSkipped();
                continue;
            }
            converted.add(facility);
        }

        facilityUpsertService.upsertAll(converted, result);
    }

    private int pageCountOf(int totalCount) {
        return pageCountOf(totalCount, PAGE_SIZE);
    }

    private int pageCountOf(
            int totalCount,
            int pageSize
    ) {
        return (totalCount + pageSize - 1) / pageSize;
    }

}
