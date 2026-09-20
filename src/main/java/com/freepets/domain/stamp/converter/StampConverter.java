package com.freepets.domain.stamp.converter;

import java.util.List;

import com.freepets.domain.stamp.dto.StampResponseDTO;
import com.freepets.domain.stamp.entity.RegionCompletion;
import com.freepets.domain.stamp.entity.Stamp;

public class StampConverter {

    private StampConverter() {}

    public static StampResponseDTO.StampSummary toStampSummary(Stamp stamp) {
        return new StampResponseDTO.StampSummary(
                stamp.getStampId(),
                stamp.getFacility().getFacilityId(),
                stamp.getFacilityName(),
                stamp.getSido(),
                stamp.getSigungu(),
                stamp.getSidoCode(),
                stamp.getSigunguCode(),
                toPetIds(stamp),
                stamp.getPhotoUrl(),
                stamp.isVerifiedOnSite(),
                stamp.getStampedAt()
        );
    }

    /**
     * @param regionCompletion 이번 도장으로 지역이 완성됐을 때만(isRegionCompleted=true) 넘긴다
     *                          — 아니면 {@code null}로, 응답에서 completionOrder/completedAt 키가
     *                          빠진다.
     */
    public static StampResponseDTO.StampResult toStampResult(
            Stamp stamp,
            boolean isNew,
            boolean isRegionCompleted,
            RegionCompletion regionCompletion
    ) {
        return new StampResponseDTO.StampResult(
                stamp.getStampId(),
                stamp.getFacility().getFacilityId(),
                stamp.getFacilityName(),
                stamp.getSido(),
                stamp.getSigungu(),
                stamp.getSidoCode(),
                stamp.getSigunguCode(),
                toPetIds(stamp),
                stamp.getPhotoUrl(),
                stamp.isVerifiedOnSite(),
                stamp.getStampedAt(),
                isNew,
                isRegionCompleted,
                regionCompletion != null ? regionCompletion.getCompletionOrder() : null,
                regionCompletion != null ? regionCompletion.getCompletedAt() : null
        );
    }

    public static StampResponseDTO.MyStamps toMyStamps(
            List<Stamp> stamps,
            StampResponseDTO.Summary summary
    ) {
        return new StampResponseDTO.MyStamps(
                stamps.stream().map(StampConverter::toStampSummary).toList(),
                summary
        );
    }

    private static List<Long> toPetIds(Stamp stamp) {
        return stamp.getStampPets().stream()
                .map(stampPet -> stampPet.getPet().getPetId())
                .toList();
    }

}
