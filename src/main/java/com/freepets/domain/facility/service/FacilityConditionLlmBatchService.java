package com.freepets.domain.facility.service;

import java.math.BigDecimal;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.PetAllowed;
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

    /**
     * {@code Facility.maxWeight}는 {@code numeric(5,2)} 컬럼이라 절댓값이 이 값 이상이면 저장이
     * 통째로 실패한다(엔티티 전체를 한 번에 UPDATE하므로 이 필드 하나 때문에 이번에 LLM이
     * 제대로 뽑아낸 다른 필드까지 다 같이 유실된다). facility_id 150처럼 규칙 엔진(#22)이
     * 예전에 잘못 뽑아 이미 DB에 들어있던 값이 원인일 수 있다 — 근본 원인 조사는 별개고,
     * 그 사이 최소한 나머지 파싱 결과라도 저장되도록 범위를 벗어난 값은 버린다.
     */
    private static final BigDecimal MAX_WEIGHT_COLUMN_LIMIT = new BigDecimal("1000");

    /**
     * 원문에 실제 체중 언급(숫자+kg류 단위)이 있는지 본다. {@code PetConditionParser.WEIGHT_LIMIT}과
     * 달리 "이하/미만" 같은 상한 표현까지는 요구하지 않는다 — maxWeight를 뒷받침할 근거가
     * 원문에 있기는 한지만 확인하는 용도라, 상한이 아닌 다른 표현(예: "10kg 이상")도 일단
     * 근거로 인정한다.
     */
    private static final Pattern WEIGHT_MENTION = Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:kg|㎏|킬로그램|킬로)");

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
                    parsed.dangerousBreedRequiredItems(),
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
     *
     * <p>{@code petAllowed == DENIED}("불가"만 해당 — 규칙 엔진이 "안내견만 가능"류는 조건부
     * 예외로 보고 PENDING으로 따로 둔다)인 시설은 LLM을 아예 안 부른다. 구조화할 조건 자체가
     * 없고, {@code PetCheckJudgeService}가 DENIED면 조건 텍스트를 안 읽으므로 결과가 어차피
     * 안 쓰인다.
     */
    private FacilityConditionLlmParseResult resolve(Facility facility) {
        if (facility.getPetAllowed() == PetAllowed.DENIED) {
            return FacilityConditionLlmParseResult.noCondition();
        }

        FacilityConditionLlmParseResult parsed = facilityConditionLlmParser.parse(
                facility.getAccompanyType(),
                facility.getAllowedAnimalText(),
                facility.getRequiredMatterText(),
                facility.getEtcAccompanyText(),
                facility.getAccidentRiskText()
        );

        // LLM이 직접 뽑은 maxWeight만 원문 근거를 검사한다 — 규칙 엔진 값은 그 자체로 실제
        // kg 매칭에서만 나오는 신뢰된 값이라 이 검사 대상이 아니다(재검증하면 규칙 엔진이
        // 이미 신뢰하기로 한 값을 LLM 출력물과 같은 기준으로 다시 의심하게 된다).
        parsed = rejectMaxWeightWithoutSourceEvidence(facility, parsed);

        if (facility.getMaxWeight() != null) {
            parsed = parsed.withMaxWeight(facility.getMaxWeight());
        }

        return rejectOutOfRangeMaxWeight(facility, parsed);
    }

    private FacilityConditionLlmParseResult rejectOutOfRangeMaxWeight(
            Facility facility,
            FacilityConditionLlmParseResult parsed
    ) {
        BigDecimal maxWeight = parsed.maxWeight();
        if (maxWeight != null && maxWeight.abs().compareTo(MAX_WEIGHT_COLUMN_LIMIT) >= 0) {
            log.warn(
                    "시설 {} maxWeight({})가 컬럼 범위(절댓값 {} 미만)를 벗어나 버립니다 — 원인 조사 필요",
                    facility.getFacilityId(), maxWeight, MAX_WEIGHT_COLUMN_LIMIT
            );
            return parsed.withMaxWeight(null);
        }
        return parsed;
    }

    /**
     * 원문에 "kg" 언급이 전혀 없는데 maxWeight가 채워져 있으면 버린다. 규칙 엔진(#22)이 뽑은
     * 값은 애초에 실제 kg 매칭에서만 나오니 걸릴 일이 없고, 이건 LLM이 "추측하지 말라"는
     * 지시를 어기고 관련 지식(예: 일반적인 맹견 기준)으로 숫자를 지어내는 사례를 막기 위한
     * 것이다 — 실제로 "맹견의 경우 입마개 착용 필수"만 있는 원문에서 maxWeight=12.00을
     * 지어내고, 그 사실을 unmappedConditionText에 스스로 남긴 사례가 관측됐다("체중 상한이
     * 명시되지 않았으나 조건 분석상 일반적 맹견 기준 반영"). 값이 0~200 범위 안이라
     * {@link #rejectOutOfRangeMaxWeight}로는 못 잡는다.
     */
    private FacilityConditionLlmParseResult rejectMaxWeightWithoutSourceEvidence(
            Facility facility,
            FacilityConditionLlmParseResult parsed
    ) {
        if (parsed.maxWeight() == null || hasWeightMention(facility)) {
            return parsed;
        }

        log.warn(
                "시설 {} maxWeight({})가 원문에 체중 언급 없이 나와 버립니다 — LLM 추측 의심",
                facility.getFacilityId(), parsed.maxWeight()
        );
        return parsed.withMaxWeight(null);
    }

    /**
     * 이미 저장된 시설 중 원문에 kg 언급 없이 maxWeight만 채워진 것을 찾아 정리한다. LLM
     * 호출도 재파싱도 없이, 이미 있는 값만 검사해서 지운다.
     *
     * <p>{@link #resolve}의 방어 코드는 그 시설이 배치에서 "다시" 처리될 때만 작동하는데,
     * 배치는 {@code petConditionStatus = NOT_PROCESSED}만 훑는다. 이미 PARSED/AMBIGUOUS로
     * 저장된 시설은 원문(관광공사 데이터)이 그대로면 재동기화를 해도 상태가 안 바뀌어
     * ({@code Facility#updateFromTourApi} 참고) 배치가 절대 다시 안 건드린다 — 그런
     * 과거 오염 데이터를 위한 일회성 청소 실행기다(예: facilityConditionCleanUpMaxWeight
     * 태스크).
     */
    public FacilityConditionCleanUpResult cleanUpMaxWeightWithoutSourceEvidence(int limit) {
        FacilityConditionCleanUpResult result = new FacilityConditionCleanUpResult();
        int page = 0;

        while (result.getChecked() < limit) {
            Slice<Facility> slice = facilityRepository.findByMaxWeightIsNotNull(PageRequest.of(page, PAGE_SIZE));

            if (slice.isEmpty()) {
                break;
            }

            for (Facility facility : slice) {
                if (result.getChecked() >= limit) {
                    break;
                }
                checkAndClean(facility, result);
            }

            log.info("maxWeight 청소 진행 {}", result.summary());

            if (!slice.hasNext()) {
                break;
            }
            page++;
        }

        log.info("maxWeight 청소를 마쳤습니다. {}", result.summary());
        return result;
    }

    private void checkAndClean(
            Facility facility,
            FacilityConditionCleanUpResult result
    ) {
        result.addChecked();

        if (hasWeightMention(facility)) {
            return;
        }

        log.warn(
                "시설 {} maxWeight({})가 원문에 체중 언급 없이 저장돼 있어 정리합니다",
                facility.getFacilityId(), facility.getMaxWeight()
        );
        facility.clearMaxWeight();
        facilityRepository.save(facility);
        result.addCleaned();
    }

    private boolean hasWeightMention(Facility facility) {
        String sourceText = String.join(" ",
                nullToEmpty(facility.getAccompanyType()),
                nullToEmpty(facility.getAllowedAnimalText()),
                nullToEmpty(facility.getRequiredMatterText()),
                nullToEmpty(facility.getEtcAccompanyText()),
                nullToEmpty(facility.getAccidentRiskText())
        );
        return WEIGHT_MENTION.matcher(sourceText).find();
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

}
