package com.freepets.domain.facility.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
     * 대상 시설을 전부 모아 배치 하나로 제출하고, 끝날 때까지 기다린 뒤 결과를 저장한다.
     *
     * <p>{@code limit}이 있으면 그만큼만 모아서 제출한다 — 전량 실행 전에 소규모로 먼저
     * 검증할 때 쓴다({@code facilityConditionParseBatchSample} 태스크).
     */
    public FacilityConditionLlmBatchApiResult run(int limit) {
        FacilityConditionLlmBatchApiResult result = new FacilityConditionLlmBatchApiResult();

        List<Facility> targets = fetchTargets(limit);
        if (targets.isEmpty()) {
            log.info("배치 제출 대상 시설이 없습니다.");
            return result;
        }

        log.info("배치 제출 대상 {}건을 모았습니다.", targets.size());
        String batchId = submit(targets);
        result.addSubmitted(targets.size());
        log.info("배치를 제출했습니다. batchId={}", batchId);

        MessageBatch finished = waitUntilEnded(batchId);
        log.info("배치 처리가 끝났습니다. requestCounts={}", finished.requestCounts());

        applyResults(batchId, result);
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

    private String submit(List<Facility> facilities) {
        OutputConfig outputConfig = FacilityConditionLlmParser.buildOutputConfig();

        BatchCreateParams.Builder builder = BatchCreateParams.builder();
        for (Facility facility : facilities) {
            builder.addRequest(requestFor(facility, outputConfig));
        }

        MessageBatch batch = anthropicClient.messages().batches().create(builder.build());
        return batch.id();
    }

    private BatchCreateParams.Request requestFor(
            Facility facility,
            OutputConfig outputConfig
    ) {
        String userMessage = FacilityConditionLlmParser.buildUserMessage(
                facility.getAccompanyType(),
                facility.getAllowedAnimalText(),
                facility.getRequiredMatterText(),
                facility.getEtcAccompanyText(),
                facility.getAccidentRiskText()
        );

        return BatchCreateParams.Request.builder()
                .customId(String.valueOf(facility.getFacilityId()))
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

            if (batch.processingStatus() == MessageBatch.ProcessingStatus.ENDED) {
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
            FacilityConditionLlmBatchApiResult result
    ) {
        try (StreamResponse<MessageBatchIndividualResponse> stream =
                anthropicClient.messages().batches().resultsStreaming(batchId)) {
            stream.stream().forEach(response -> applyOne(response, result));
        }
    }

    private void applyOne(
            MessageBatchIndividualResponse response,
            FacilityConditionLlmBatchApiResult result
    ) {
        Long facilityId;
        try {
            facilityId = Long.valueOf(response.customId());
        } catch (NumberFormatException exception) {
            log.warn("배치 결과의 customId가 시설 ID 형식이 아닙니다: {}", response.customId());
            result.addFailed();
            return;
        }

        Optional<Facility> maybeFacility = facilityRepository.findById(facilityId);
        if (maybeFacility.isEmpty()) {
            log.warn("배치 결과에 해당하는 시설을 찾을 수 없습니다. facilityId={}", facilityId);
            result.addFailed();
            return;
        }
        Facility facility = maybeFacility.get();

        try {
            FacilityConditionLlmParseResult parsed = parse(response.result(), facility);

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
        } catch (Exception exception) {
            log.warn("시설 {} 배치 결과 적용 실패 — 건너뜁니다: {}", facilityId, exception.getMessage());
            result.addFailed();
        }
    }

    private FacilityConditionLlmParseResult parse(
            MessageBatchResult batchResult,
            Facility facility
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

        return FacilityConditionGuard.apply(facility, parsed);
    }

}
