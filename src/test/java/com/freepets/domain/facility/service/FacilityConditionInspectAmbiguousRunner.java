package com.freepets.domain.facility.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
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
    @DisabledIfSystemProperty(
            named = "facility.condition.inspectAmbiguous.parsed",
            matches = "true",
            disabledReason = "체중 상한이 정상적으로 뽑힌 PARSED 시설을 보는 실행(facilityConditionInspectParsedWeight)에서는 건너뛴다."
    )
    void 애매함_시설의_원문과_미매핑_텍스트를_출력한다() {
        int limit = Integer.getInteger("facility.condition.inspectAmbiguous.limit", 10);
        Slice<Facility> slice = facilityRepository.findByPetConditionStatus(PetConditionStatus.AMBIGUOUS, sortedByRecent(limit));

        printAll(slice);
    }

    /**
     * 원문에 kg 언급이 없는데 LLM이 maxWeight를 지어내는 사례를 막는 방어 코드를 넣은 뒤,
     * 반대로 원문에 실제 체중 제한이 있는 정상 케이스는 여전히 잘 뽑히는지 실제 데이터로
     * 확인하는 검증 전용 실행기다 — {@code facilityConditionInspectAmbiguous}는 AMBIGUOUS만
     * 보여줘서, 깔끔하게 다 매핑돼 PARSED로 빠진 정상 케이스는 거기서 안 보인다.
     */
    @Test
    @DisplayName("체중 상한이 정상적으로 뽑힌 PARSED 시설을 출력한다")
    @EnabledIfSystemProperty(
            named = "facility.condition.inspectAmbiguous.parsed",
            matches = "true",
            disabledReason = "검증 전용 실행기. ./gradlew facilityConditionInspectParsedWeight 로 실행한다."
    )
    void 체중_상한이_정상적으로_뽑힌_PARSED_시설을_출력한다() {
        int limit = Integer.getInteger("facility.condition.inspectAmbiguous.limit", 10);
        Slice<Facility> slice = facilityRepository.findByPetConditionStatusAndMaxWeightIsNotNull(
                PetConditionStatus.PARSED, sortedByRecent(limit)
        );

        printAll(slice);
    }

    // updatedAt 내림차순 — 정렬 기준이 없으면 매번 같은(대체로 facility_id가 낮은) 행만
    // 보여줘서 방금 돌린 배치의 결과를 확인할 수가 없다. 가장 최근에 처리된 것부터 본다.
    private Pageable sortedByRecent(int limit) {
        return PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    private void printAll(Slice<Facility> slice) {
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
            System.out.println("맹견전용요구사항: " + facility.getDangerousBreedRequiredItems());
            System.out.println("미매핑 텍스트: " + facility.getUnmappedConditionText());
        }
        System.out.println("========================================");
        System.out.println("총 " + slice.getNumberOfElements() + "건 출력");
    }

}
