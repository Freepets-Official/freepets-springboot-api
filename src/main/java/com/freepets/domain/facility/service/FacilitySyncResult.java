package com.freepets.domain.facility.service;

import java.util.EnumMap;
import java.util.Map;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.PetAllowed;

import lombok.Getter;

/**
 * 적재 결과 집계. 적재가 끝난 뒤 분포를 확인해 이상을 잡아낸다.
 *
 * <p>{@code DENIED}는 원문에 명시적 불가 표현이 있는 소수 건만 나와야 하고,
 * {@code PENDING}은 반려동물 동반 정보가 없는 시설이므로 다수를 차지하는 것이 정상이다.
 */
@Getter
public class FacilitySyncResult {

    private int inserted;
    private int updated;

    /** 적재 대상이 아닌 분류(여행코스 등)로 건너뛴 건수. */
    private int skipped;

    private final Map<PetAllowed, Integer> petAllowedCounts = new EnumMap<>(PetAllowed.class);

    private int petTourListed;

    public void addInserted(Facility facility) {
        inserted++;
        count(facility);
    }

    public void addUpdated(Facility facility) {
        updated++;
        count(facility);
    }

    public void addSkipped() {
        skipped++;
    }

    public int total() {
        return inserted + updated;
    }

    public String summary() {
        return "신규 %d, 갱신 %d, 제외 %d | 동반가능 %d, 동반불가 %d, 확인필요 %d | 펫정보 등재 %d"
                .formatted(
                        inserted,
                        updated,
                        skipped,
                        countOf(PetAllowed.ALLOWED),
                        countOf(PetAllowed.DENIED),
                        countOf(PetAllowed.PENDING),
                        petTourListed
                );
    }

    public int countOf(PetAllowed petAllowed) {
        return petAllowedCounts.getOrDefault(petAllowed, 0);
    }

    private void count(Facility facility) {
        petAllowedCounts.merge(facility.getPetAllowed(), 1, Integer::sum);
        if (facility.isPetTourListed()) {
            petTourListed++;
        }
    }

}
