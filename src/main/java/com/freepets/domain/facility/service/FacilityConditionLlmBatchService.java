package com.freepets.domain.facility.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.PetConditionStatus;
import com.freepets.domain.facility.repository.FacilityRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code petConditionStatus = NOT_PROCESSED}인 시설을 훑어 {@link FacilityConditionLlmParser}로
 * 조건 원문을 구조화하고 저장한다. 신규 시설 동기화(lazy-sync) 연동은 이 클래스 범위 밖 —
 * 지금 있는 시설(적재 직후 전부 NOT_PROCESSED)을 한 번에 처리하는 배치 실행기다.
 *
 * <p>외부 API(Claude)를 시설마다 호출하므로 {@code test} 태스크에서는 실행되지 않는다.
 * 전용 태스크로만 돌린다.
 *
 * <pre>
 * ./gradlew facilityConditionParse
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityConditionLlmBatchService {

    private static final int PAGE_SIZE = 50;

    private final FacilityRepository facilityRepository;
    private final FacilityConditionLlmParser facilityConditionLlmParser;

    public FacilityConditionLlmBatchResult parseAll() {
        FacilityConditionLlmBatchResult result = new FacilityConditionLlmBatchResult();

        while (true) {
            Slice<Facility> slice = facilityRepository.findByPetConditionStatus(
                    PetConditionStatus.NOT_PROCESSED, Pageable.ofSize(PAGE_SIZE)
            );

            if (slice.isEmpty()) {
                break;
            }

            int succeededBefore = result.getProcessed();
            slice.forEach(facility -> parseAndSave(facility, result));
            int succeededThisPage = result.getProcessed() - succeededBefore;

            log.info("조건 파싱 진행 {}", result.summary());

            // 매번 같은 페이지(0페이지)만 다시 조회한다 — 성공한 건은 상태가 바뀌어 다음 조회에서
            // 자연히 빠지기 때문이다. 실패는 상태를 안 바꾸므로 실패 건수는 "진행"으로 치면 안 된다
            // (계속 세기만 하면 매번 달라져 종료 조건이 거짓이 된다) — 성공 건수만으로 판단해야
            // 이 페이지가 전부 실패했을 때 같은 결과가 반복되는 무한 루프를 막을 수 있다.
            if (succeededThisPage == 0) {
                log.warn("이번 페이지가 전부 실패해 배치를 중단합니다. {}", result.summary());
                break;
            }
        }

        log.info("조건 파싱을 마쳤습니다. {}", result.summary());
        return result;
    }

    private void parseAndSave(
            Facility facility,
            FacilityConditionLlmBatchResult result
    ) {
        try {
            FacilityConditionLlmParseResult parsed = resolve(facility);

            facility.applyParsedCondition(
                    parsed.status(),
                    parsed.maxWeight(),
                    parsed.isDangerousBreedExcluded(),
                    parsed.requiredItems(),
                    parsed.partialAreaNote(),
                    parsed.unmappedConditionText()
            );

            facilityRepository.save(facility);
            result.add(parsed.status());
        } catch (Exception e) {
            log.warn("시설 {} 조건 파싱 실패 — 건너뜁니다: {}", facility.getFacilityId(), e.getMessage());
            result.addFailed();
        }
    }

    /**
     * 규칙 엔진(PetConditionParser, #22)이 이미 maxWeight나 요구조건(checkLists)을 뽑아낸
     * 시설은 정형화가 이미 끝난 것으로 보고 LLM을 호출하지 않는다 — 안 그러면 이미 정확한
     * Facility.maxWeight를 LLM의 별도 판독값으로 조건 없이 덮어써버리게 된다. 원문은 있는데
     * 규칙 엔진이 아무것도 못 뽑아낸(진짜 애매한) 경우에만 실제로 Claude를 호출한다.
     */
    private FacilityConditionLlmParseResult resolve(Facility facility) {
        boolean alreadyResolvedByRuleEngine = facility.getMaxWeight() != null
                || !facility.getCheckLists().isEmpty();

        if (alreadyResolvedByRuleEngine) {
            return FacilityConditionLlmParseResult.alreadyResolvedByRuleEngine(facility.getMaxWeight());
        }

        return facilityConditionLlmParser.parse(
                facility.getAccompanyType(),
                facility.getAllowedAnimalText(),
                facility.getRequiredMatterText(),
                facility.getEtcAccompanyText(),
                facility.getAccidentRiskText()
        );
    }

}
