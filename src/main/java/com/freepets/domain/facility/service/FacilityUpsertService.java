package com.freepets.domain.facility.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.entity.CheckList;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.facility.repository.FacilityRepository;

import lombok.RequiredArgsConstructor;

/**
 * 변환된 시설을 페이지 단위로 저장한다.
 *
 * <p>{@link FacilitySyncService}와 분리한 이유는 트랜잭션 때문이다. 같은 클래스 안에서 호출하면
 * 스프링 프록시를 타지 않아 {@code @Transactional}이 걸리지 않는다.
 *
 * <p>페이지마다 트랜잭션을 끊어야 전체 적재가 하나의 거대한 트랜잭션이 되지 않고,
 * 중간에 끊겨도 이미 저장된 페이지는 남는다.
 */
@Service
@RequiredArgsConstructor
public class FacilityUpsertService {

    private final FacilityRepository facilityRepository;

    @Transactional
    public void upsertAll(
            List<Facility> converted,
            FacilitySyncResult result
    ) {
        Map<String, Facility> existing = findExistingBy(converted);
        List<Facility> toInsert = new ArrayList<>();

        for (Facility facility : converted) {
            Facility found = existing.get(facility.getContentId());

            if (found == null) {
                toInsert.add(facility);
                result.addInserted(facility);
                continue;
            }

            // 리뷰 집계·신뢰도 같은 우리 데이터는 그대로 두고 관광공사 값만 갱신한다.
            found.updateFromTourApi(facility);
            found.replaceRequirements(requirementsOf(facility));
            result.addUpdated(facility);
        }

        facilityRepository.saveAll(toInsert);
    }

    private List<Requirement> requirementsOf(Facility facility) {
        return facility.getCheckLists().stream()
                .map(CheckList::getType)
                .toList();
    }

    private Map<String, Facility> findExistingBy(List<Facility> converted) {
        List<String> contentIds = converted.stream()
                .map(Facility::getContentId)
                .filter(contentId -> contentId != null)
                .toList();

        if (contentIds.isEmpty()) {
            return Map.of();
        }

        Map<String, Facility> existing = new HashMap<>();
        facilityRepository.findByContentIdIn(contentIds)
                .forEach(facility -> existing.put(facility.getContentId(), facility));
        return existing;
    }

}
