package com.freepets.domain.facility.service;

import java.util.function.Function;

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
        return parseUpTo(Integer.MAX_VALUE);
    }

    /**
     * {@code limit}건을 처리하면 NOT_PROCESSED가 남아 있어도 멈춘다 — 전량 실행 전에 소규모로
     * 먼저 검증해볼 때 쓴다(예: {@code facilityConditionParseSample} 태스크).
     *
     * <p>facility_id 순서로 훑기 때문에, NOT_PROCESSED의 약 90%를 차지하는 조건없음 시설이
     * 앞쪽에 몰려 있으면 이 메소드만으론 LLM이 실제로 호출되는 케이스를 못 볼 수 있다 — 그런
     * 검증에는 {@link #parseSampleWithConditionText}를 쓴다.
     */
    public FacilityConditionLlmBatchResult parseUpTo(int limit) {
        return run(limit, pageable -> facilityRepository.findByPetConditionStatus(
                PetConditionStatus.NOT_PROCESSED, pageable
        ));
    }

    /**
     * 조건 원문이 하나라도 있는 NOT_PROCESSED 시설만 골라 {@code limit}건 처리한다 — LLM이
     * 실제로 호출되는 케이스만 보장하는 검증용(예: {@code facilityConditionParseSampleWithCondition}
     * 태스크). {@link #parseUpTo}는 facility_id 순서를 그대로 따르므로 조건없음 시설(약 90%)이
     * 앞쪽에 몰려 있으면 검증에 못 쓸 수 있다.
     */
    public FacilityConditionLlmBatchResult parseSampleWithConditionText(int limit) {
        return run(limit, pageable -> facilityRepository.findByPetConditionStatusWithConditionText(
                PetConditionStatus.NOT_PROCESSED, pageable
        ));
    }

    private FacilityConditionLlmBatchResult run(
            int limit,
            Function<Pageable, Slice<Facility>> fetchPage
    ) {
        FacilityConditionLlmBatchResult result = new FacilityConditionLlmBatchResult();

        while (result.getProcessed() < limit) {
            Slice<Facility> slice = fetchPage.apply(Pageable.ofSize(PAGE_SIZE));

            if (slice.isEmpty()) {
                break;
            }

            int succeededBefore = result.getProcessed();
            for (Facility facility : slice) {
                if (result.getProcessed() >= limit) {
                    break;
                }
                parseAndSave(facility, result);
            }
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
     * LLM은 항상 호출한다 — 규칙 엔진(PetConditionParser, #22)이 maxWeight나 요구조건 일부를
     * 뽑아냈다고 해서 원문 전체를 다 처리했다는 뜻은 아니기 때문이다. 예를 들어 체중 제한과
     * "맹견 제외"가 같은 원문에 같이 있으면, 규칙 엔진은 체중만 뽑고 맹견 배제 여부는 규칙
     * 엔진 자체가 다루지 않는 개념이라 LLM을 건너뛰면 그 정보가 영영 사라진다(다음 배치도
     * 같은 이유로 다시 건너뛰기 때문에 복구가 안 됨).
     *
     * <p>다만 maxWeight만큼은 규칙 엔진 값을 우선한다 — 실측 데이터로 검증된 정규식 결과가
     * 매번 새로 읽는 LLM 결과보다 신뢰도가 높고, 판별 엔진(PetCheckJudgeService)이 그 값을
     * 그대로 읽으므로 실행할 때마다 결과가 흔들리면 안 된다.
     */
    private FacilityConditionLlmParseResult resolve(Facility facility) {
        FacilityConditionLlmParseResult parsed = facilityConditionLlmParser.parse(
                facility.getAccompanyType(),
                facility.getAllowedAnimalText(),
                facility.getRequiredMatterText(),
                facility.getEtcAccompanyText(),
                facility.getAccidentRiskText()
        );

        if (facility.getMaxWeight() != null) {
            return parsed.withMaxWeight(facility.getMaxWeight());
        }

        return parsed;
    }

}
