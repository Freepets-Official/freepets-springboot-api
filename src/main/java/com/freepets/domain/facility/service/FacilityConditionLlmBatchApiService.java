package com.freepets.domain.facility.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.batches.BatchCreateParams;
import com.anthropic.models.messages.batches.MessageBatch;
import com.anthropic.models.messages.batches.MessageBatchIndividualResponse;
import com.anthropic.models.messages.batches.MessageBatchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.PetConditionStatus;
import com.freepets.domain.facility.repository.FacilityRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code petConditionStatus = NOT_PROCESSED}이면서 실제로 LLM 파싱이 필요한 시설을 Anthropic
 * Batch API로 한 번에 처리한다. {@link FacilityConditionLlmBatchService}(동기 경로)와 로직은
 * 같지만, 시설마다 개별 호출하는 대신 전량을 하나의 배치로 제출해 입력·출력 토큰을 50%
 * 할인받는다 — 실시간 응답이 필요 없는 순수 백그라운드 작업이라 이 할인에 맞는 경우다.
 *
 * <p>{@code pet_condition_hash}(원문 5종 필드의 해시)가 같은 시설은 원문이 완전히 동일해
 * {@link FacilityConditionLlmParser}의 결과도 항상 같다 — 같은 조건 문장을 쓰는 시설이 많아서
 * (docs/03 3장), 해시가 같은 시설을 묶어 요청 하나만 보내고 결과를 그룹 전체에 적용한다.
 * 시설 수만큼이 아니라 고유 해시 수만큼만 Claude를 호출한다.
 *
 * <p>대상 선정은 {@link FacilityRepository#findRequiringLlmParse}가 담당하고, 프롬프트·모델·
 * 구조화 출력 스키마·maxWeight 방어 로직은 {@link FacilityConditionLlmParser}와
 * {@link FacilityConditionGuard}를 그대로 재사용한다 — 동기 경로와 결과가 갈리면 안 된다.
 *
 * <p>외부 API(Claude)를 호출하고 처리에 수 분~수 시간이 걸릴 수 있어 {@code test} 태스크에서는
 * 실행되지 않는다. 전용 태스크로만 돌린다.
 *
 * <pre>
 * ./gradlew facilityConditionParseBatch
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityConditionLlmBatchApiService {

    private static final int FETCH_PAGE_SIZE = 1000;
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(30);

    /** Anthropic이 보장하는 처리 시한(최대 24시간)에 여유를 두고 자른다. */
    private static final Duration MAX_WAIT = Duration.ofHours(20);

    private final FacilityRepository facilityRepository;
    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 대상 시설을 전부 모아 조건 해시로 묶은 뒤, 고유 해시 수만큼만 배치로 제출하고 끝날
     * 때까지 기다려 결과를 그룹 전체에 적용한다.
     *
     * <p>{@code limit}이 있으면 그만큼만(시설 수 기준) 모아서 제출한다 — 전량 실행 전에
     * 소규모로 먼저 검증할 때 쓴다({@code facilityConditionParseBatchSample} 태스크).
     */
    public FacilityConditionLlmBatchApiResult run(int limit) {
        FacilityConditionLlmBatchApiResult result = new FacilityConditionLlmBatchApiResult();

        List<Facility> targets = fetchTargets(limit);
        if (targets.isEmpty()) {
            log.info("배치 제출 대상 시설이 없습니다.");
            return result;
        }

        Map<String, List<Facility>> groupedByHash = groupByConditionHash(targets);
        log.info(
                "배치 제출 대상 {}건을 모았습니다 — 조건 해시 기준 {}건으로 묶어서 제출합니다.",
                targets.size(), groupedByHash.size()
        );

        String batchId = submit(groupedByHash);
        result.addSubmitted(targets.size());
        log.info("배치를 제출했습니다. batchId={}", batchId);

        MessageBatch finished = waitUntilEnded(batchId);
        log.info("배치 처리가 끝났습니다. requestCounts={}", finished.requestCounts());

        applyResults(batchId, groupedByHash, result);
        log.info("배치 결과 적용을 마쳤습니다. {}", result.summary());
        return result;
    }

    private List<Facility> fetchTargets(int limit) {
        List<Facility> targets = new ArrayList<>();
        int page = 0;

        while (targets.size() < limit) {
            Pageable pageable = PageRequest.of(page, Math.min(FETCH_PAGE_SIZE, limit - targets.size()));
            Slice<Facility> slice = facilityRepository.findRequiringLlmParse(
                    PetConditionStatus.NOT_PROCESSED, pageable
            );

            targets.addAll(slice.getContent());

            if (!slice.hasNext()) {
                break;
            }
            page++;
        }

        return targets;
    }

    /**
     * {@code pet_condition_hash}로 묶는다. 원문 5종 필드가 전부 같아야 같은 해시가 나오므로
     * ({@code TourApiFacilityConverter#hashOf}), 같은 그룹의 시설은 파싱 결과도 항상 같다.
     *
     * <p>해시가 없는 시설(이론상 나올 일이 없다 — 대상 선정 자체가 원문이 있는 시설만 고른다)은
     * 안전하게 시설 ID로 자기만의 그룹을 만든다. null을 그대로 묶는 키로 쓰면 서로 다른
     * 원문의 시설이 우연히 한 그룹으로 섞일 수 있다.
     */
    private Map<String, List<Facility>> groupByConditionHash(List<Facility> targets) {
        Map<String, List<Facility>> grouped = new LinkedHashMap<>();
        for (Facility facility : targets) {
            grouped.computeIfAbsent(groupKeyOf(facility), key -> new ArrayList<>()).add(facility);
        }
        return grouped;
    }

    private String groupKeyOf(Facility facility) {
        String hash = facility.getPetConditionHash();
        return (hash != null && !hash.isBlank()) ? hash : "facility:" + facility.getFacilityId();
    }

    private String submit(Map<String, List<Facility>> groupedByHash) {
        OutputConfig outputConfig = FacilityConditionLlmParser.buildOutputConfig();

        BatchCreateParams.Builder builder = BatchCreateParams.builder();
        for (Map.Entry<String, List<Facility>> entry : groupedByHash.entrySet()) {
            // 같은 그룹의 시설은 원문이 전부 동일하므로, 대표로 첫 시설의 원문만 보내면 된다.
            Facility representative = entry.getValue().get(0);
            builder.addRequest(requestFor(entry.getKey(), representative, outputConfig));
        }

        MessageBatch batch = anthropicClient.messages().batches().create(builder.build());
        return batch.id();
    }

    private BatchCreateParams.Request requestFor(
            String groupKey,
            Facility representative,
            OutputConfig outputConfig
    ) {
        String userMessage = FacilityConditionLlmParser.buildUserMessage(
                representative.getAccompanyType(),
                representative.getAllowedAnimalText(),
                representative.getRequiredMatterText(),
                representative.getEtcAccompanyText(),
                representative.getAccidentRiskText()
        );

        return BatchCreateParams.Request.builder()
                .customId(groupKey)
                .params(BatchCreateParams.Request.Params.builder()
                        .model(FacilityConditionLlmParser.MODEL)
                        .maxTokens(FacilityConditionLlmParser.MAX_TOKENS)
                        .system(FacilityConditionLlmParser.SYSTEM_PROMPT)
                        .outputConfig(outputConfig)
                        .addUserMessage(userMessage)
                        .build())
                .build();
    }

    private MessageBatch waitUntilEnded(String batchId) {
        Instant deadline = Instant.now().plus(MAX_WAIT);

        while (true) {
            MessageBatch batch = anthropicClient.messages().batches().retrieve(batchId);

            // ProcessingStatus는 진짜 자바 enum이 아니라 미지의 값도 담을 수 있는 래퍼 타입이라,
            // JSON 역직렬화로 만들어진 인스턴스가 이 상수와 같은 참조라는 보장이 없다 — ==로
            // 비교하면 "ended"를 실제로 받고도 계속 폴링을 반복하는 무한루프가 된다(직접 겪음).
            if (batch.processingStatus().equals(MessageBatch.ProcessingStatus.ENDED)) {
                return batch;
            }
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException(
                        "배치 처리 대기 시간을 초과했습니다. batchId=" + batchId
                                + ", status=" + batch.processingStatus()
                );
            }

            log.info("배치 처리 중입니다... batchId={}, status={}", batchId, batch.processingStatus());
            sleep(POLL_INTERVAL);
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("배치 대기가 중단되었습니다.", exception);
        }
    }

    private void applyResults(
            String batchId,
            Map<String, List<Facility>> groupedByHash,
            FacilityConditionLlmBatchApiResult result
    ) {
        try (StreamResponse<MessageBatchIndividualResponse> stream =
                anthropicClient.messages().batches().resultsStreaming(batchId)) {
            stream.stream().forEach(response -> applyOne(response, groupedByHash, result));
        }
    }

    /**
     * 응답 하나(그룹 하나)를 파싱해서 같은 조건 해시를 공유하는 시설 전부에 적용한다 — 원문이
     * 동일하므로 파싱 결과도 그룹 내 모든 시설에 그대로 유효하다.
     */
    private void applyOne(
            MessageBatchIndividualResponse response,
            Map<String, List<Facility>> groupedByHash,
            FacilityConditionLlmBatchApiResult result
    ) {
        List<Facility> group = groupedByHash.get(response.customId());
        if (group == null || group.isEmpty()) {
            log.warn("배치 결과의 customId에 해당하는 시설 그룹을 찾을 수 없습니다: {}", response.customId());
            result.addFailed();
            return;
        }

        try {
            FacilityConditionLlmParseResult parsed = parse(response.result(), group.get(0));

            for (Facility facility : group) {
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
                result.addApplied(parsed.status());
            }
        } catch (Exception exception) {
            log.warn(
                    "그룹 {}(시설 {}건) 배치 결과 적용 실패 — 건너뜁니다: {}",
                    response.customId(), group.size(), exception.getMessage()
            );
            for (int i = 0; i < group.size(); i++) {
                result.addFailed();
            }
        }
    }

    private FacilityConditionLlmParseResult parse(
            MessageBatchResult batchResult,
            Facility representative
    ) throws Exception {
        if (!batchResult.isSucceeded()) {
            throw new IllegalStateException("배치 개별 요청이 실패했습니다: " + batchResult);
        }

        Message message = batchResult.asSucceeded().message();
        String json = message.content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .map(textBlock -> textBlock.text())
                .orElseThrow(() -> new IllegalStateException("배치 응답에 텍스트 블록이 없습니다."));

        FacilityConditionExtraction extraction = objectMapper.readValue(json, FacilityConditionExtraction.class);
        FacilityConditionLlmParseResult parsed = FacilityConditionLlmParseResult.fromExtraction(extraction);

        return FacilityConditionGuard.apply(representative, parsed);
    }

}
