package com.freepets.domain.facility.converter;

import java.util.List;
import java.util.Map;

import com.freepets.domain.facility.dto.FacilityResponseDTO;
import com.freepets.domain.facility.entity.CheckList;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.PetFriendlyGrade;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.facility.repository.FacilityWithDistance;

public class FacilityConverter {

    private FacilityConverter() {}

    public static FacilityResponseDTO.FacilitySummary toFacilitySummary(
            FacilityWithDistance facilityWithDistance,
            long reviewCount
    ) {
        Facility facility = facilityWithDistance.facility();

        List<Requirement> requirements = facility.getCheckLists().stream()
                .map(CheckList::getType)
                .toList();

        return new FacilityResponseDTO.FacilitySummary(
                facility.getFacilityId(),
                facility.getName(),
                facility.getCategory(),
                facility.getAddress(),
                Math.round(facilityWithDistance.distanceMeter()),
                facility.getPetAllowed(),
                facility.getMaxWeight(),
                requirements,
                facility.getPetScore(),
                PetFriendlyGrade.labelOf(facility.getPetScore(), reviewCount),
                reviewCount
        );
    }

    /**
     * @param reviewCounts 시설 ID → 리뷰 수. 리뷰가 없는 시설은 담겨 있지 않으므로 0으로 채운다.
     * @param total 페이징 이전, 조건에 맞는 전체 시설 수
     */
    public static FacilityResponseDTO.FacilitySearchResult toFacilitySearchResult(
            List<FacilityWithDistance> facilities,
            Map<Long, Long> reviewCounts,
            long total
    ) {
        List<FacilityResponseDTO.FacilitySummary> items = facilities.stream()
                .map(facility -> toFacilitySummary(
                        facility,
                        reviewCounts.getOrDefault(facility.facility().getFacilityId(), 0L)
                ))
                .toList();

        return new FacilityResponseDTO.FacilitySearchResult(items, total);
    }
}
