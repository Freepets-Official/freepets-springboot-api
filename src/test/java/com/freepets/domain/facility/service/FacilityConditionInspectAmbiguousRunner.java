package com.freepets.domain.facility.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.PetConditionStatus;
import com.freepets.domain.facility.repository.FacilityRepository;

/**
 * AMBIGUOUS로 판정된 시설의 원문 5개 필드와 {@code unmappedConditionText}를 나란히 출력한다 —
 * LLM이 원문의 어느 부분을 컬럼으로 못 담았는지 눈으로 확인하는 조회 전용 실행기.
 *
 * <p>LLM 호출도 저장도 없는 순수 읽기라 비용이 들지 않는다. {@code test} 태스크에서는 실행되지
 * 않고 전용 태스크로만 돌린다.
 *
 * <pre>
 * ./gradlew facilityConditionInspectAmbiguous
 * </pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(
        named = "facility.condition.inspectAmbiguous",
        matches = "true",
        disabledReason = "조회 전용 실행기. ./gradlew facilityConditionInspectAmbiguous 로 실행한다."
)
class FacilityConditionInspectAmbiguousRunner {

    @Autowired
    private FacilityRepository facilityRepository;

    @Test
    @DisplayName("애매함(AMBIGUOUS) 시설의 원문과 미매핑 텍스트를 출력한다")
    void 애매함_시설의_원문과_미매핑_텍스트를_출력한다() {
        int limit = Integer.getInteger("facility.condition.inspectAmbiguous.limit", 10);

        // updatedAt 내림차순 — 정렬 기준이 없으면 매번 같은(대체로 facility_id가 낮은) 행만
        // 보여줘서 방금 돌린 배치의 결과를 확인할 수가 없다. 가장 최근에 처리된 것부터 본다.
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Slice<Facility> slice = facilityRepository.findByPetConditionStatus(
                PetConditionStatus.AMBIGUOUS, pageable
        );

        for (Facility facility : slice) {
            System.out.println("========================================");
            System.out.println("시설 " + facility.getFacilityId() + " (" + facility.getName() + ")");
            System.out.println("----- 원문 -----");
            System.out.println("동반구분: " + facility.getAccompanyType());
            System.out.println("동반가능동물: " + facility.getAllowedAnimalText());
            System.out.println("필요사항: " + facility.getRequiredMatterText());
            System.out.println("기타동반정보: " + facility.getEtcAccompanyText());
            System.out.println("사고대비사항: " + facility.getAccidentRiskText());
            System.out.println("----- 파싱 결과 -----");
            System.out.println("petAllowed: " + facility.getPetAllowed());
            System.out.println("maxWeight: " + facility.getMaxWeight());
            System.out.println("맹견배제: " + facility.isDangerousBreedExcluded());
            System.out.println("미매핑 텍스트: " + facility.getUnmappedConditionText());
        }
        System.out.println("========================================");
        System.out.println("총 " + slice.getNumberOfElements() + "건 출력");
    }

}
