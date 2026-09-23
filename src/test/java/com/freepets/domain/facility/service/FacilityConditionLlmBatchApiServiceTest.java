package com.freepets.domain.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.batches.BatchCreateParams;
import com.anthropic.models.messages.batches.MessageBatch;
import com.anthropic.models.messages.batches.MessageBatchIndividualResponse;
import com.anthropic.models.messages.batches.MessageBatchRequestCounts;
import com.anthropic.services.blocking.MessageService;
import com.anthropic.services.blocking.messages.BatchService;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.PetConditionStatus;
import com.freepets.domain.facility.repository.FacilityRepository;

// 배치 제출(요청 개수), 폴링 종료 조건, 결과 없음 처리만 검증한다. 실제 LLM 응답 파싱(customId
// 매칭, 구조화 결과 적용)까지는 SDK 응답 객체를 통째로 흉내 내야 해서 여기서 하지 않는다 —
// FacilityConditionLlmParserTest와 같은 이유로, 실제 파싱 결과 검증은 소규모 실행
// (facilityConditionParseBatchSample)으로 한다.
@ExtendWith(MockitoExtension.class)
class FacilityConditionLlmBatchApiServiceTest {

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private AnthropicClient anthropicClient;

    @Mock
    private MessageService messageService;

    @Mock
    private BatchService batchService;

    @InjectMocks
    private FacilityConditionLlmBatchApiService facilityConditionLlmBatchApiService;

    @Test
    void 대상_시설이_없으면_배치를_제출하지_않는다() {
        when(facilityRepository.findRequiringLlmParse(eq(PetConditionStatus.NOT_PROCESSED), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of()));

        FacilityConditionLlmBatchApiResult result = facilityConditionLlmBatchApiService.run(Integer.MAX_VALUE);

        assertThat(result.getSubmitted()).isZero();
        verifyNoInteractions(anthropicClient);
    }

    @Test
    void 원문이_다른_시설은_각각_요청으로_제출한다() {
        Facility facility1 = facility(1L, "전 견종 동반 가능");
        Facility facility2 = facility(2L, "10kg 이하만 동반 가능");

        when(facilityRepository.findRequiringLlmParse(eq(PetConditionStatus.NOT_PROCESSED), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(facility1, facility2)));

        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.batches()).thenReturn(batchService);

        ArgumentCaptor<BatchCreateParams> paramsCaptor = ArgumentCaptor.forClass(BatchCreateParams.class);
        when(batchService.create(paramsCaptor.capture())).thenReturn(endedBatch("batch_test", 2));
        when(batchService.retrieve(anyString())).thenReturn(endedBatch("batch_test", 2));

        StreamResponse<MessageBatchIndividualResponse> emptyStream = mockEmptyStream();
        when(batchService.resultsStreaming(anyString())).thenReturn(emptyStream);

        FacilityConditionLlmBatchApiResult result = facilityConditionLlmBatchApiService.run(Integer.MAX_VALUE);

        assertThat(result.getSubmitted()).isEqualTo(2);
        assertThat(paramsCaptor.getValue().requests()).hasSize(2);
        assertThat(paramsCaptor.getValue().requests())
                .extracting(request -> request.customId())
                .doesNotHaveDuplicates();
        verify(batchService).create(any(BatchCreateParams.class));
    }

    @Test
    void 원문이_같은_시설은_요청_하나로_묶어_제출한다() {
        // 프롬프트가 완전히 같으면 파싱 결과도 항상 같다 — 그걸 알면서 시설마다 따로 호출하면
        // API 비용만 늘어난다.
        Facility facility1 = facility(1L, "전 견종 동반 가능");
        Facility facility2 = facility(2L, "전 견종 동반 가능");
        Facility facility3 = facility(3L, "10kg 이하만 동반 가능");

        when(facilityRepository.findRequiringLlmParse(eq(PetConditionStatus.NOT_PROCESSED), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(facility1, facility2, facility3)));

        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.batches()).thenReturn(batchService);

        ArgumentCaptor<BatchCreateParams> paramsCaptor = ArgumentCaptor.forClass(BatchCreateParams.class);
        when(batchService.create(paramsCaptor.capture())).thenReturn(endedBatch("batch_test", 2));
        when(batchService.retrieve(anyString())).thenReturn(endedBatch("batch_test", 2));

        StreamResponse<MessageBatchIndividualResponse> emptyStream = mockEmptyStream();
        when(batchService.resultsStreaming(anyString())).thenReturn(emptyStream);

        FacilityConditionLlmBatchApiResult result = facilityConditionLlmBatchApiService.run(Integer.MAX_VALUE);

        // 제출 집계는 시설 수 기준(3)이지만, 실제로 나간 요청은 고유 프롬프트 수만큼(2)이어야 한다.
        assertThat(result.getSubmitted()).isEqualTo(3);
        assertThat(paramsCaptor.getValue().requests()).hasSize(2);
    }

    @Test
    void 관광공사_원문이_같아도_정리_안내문이_다르면_따로_제출한다() {
        // #148 회귀 방지 — pet_condition_hash는 관광공사 원문 5종만 담아서, 그 해시로 묶으면
        // 관광공사 원문이 똑같이 비어 있고 정리 안내문만 다른 시설들이 한 그룹이 돼 서로 남의
        // 파싱 결과를 받는다.
        Facility facility1 = facilityWithConditionRaw(1L, "전 구역에서 모든 견종이 이용할 수 있습니다.");
        Facility facility2 = facilityWithConditionRaw(2L, "일부 구역에 한해 9kg 이하만 이용할 수 있습니다.");

        when(facilityRepository.findRequiringLlmParse(eq(PetConditionStatus.NOT_PROCESSED), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(facility1, facility2)));

        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.batches()).thenReturn(batchService);

        ArgumentCaptor<BatchCreateParams> paramsCaptor = ArgumentCaptor.forClass(BatchCreateParams.class);
        when(batchService.create(paramsCaptor.capture())).thenReturn(endedBatch("batch_test", 2));
        when(batchService.retrieve(anyString())).thenReturn(endedBatch("batch_test", 2));

        StreamResponse<MessageBatchIndividualResponse> emptyStream = mockEmptyStream();
        when(batchService.resultsStreaming(anyString())).thenReturn(emptyStream);

        facilityConditionLlmBatchApiService.run(Integer.MAX_VALUE);

        assertThat(paramsCaptor.getValue().requests()).hasSize(2);
        assertThat(paramsCaptor.getValue().requests())
                .extracting(request -> request.customId())
                .doesNotHaveDuplicates();
    }

    @Test
    void limit을_주면_그만큼만_제출한다() {
        Facility facility1 = facility(1L);

        when(facilityRepository.findRequiringLlmParse(eq(PetConditionStatus.NOT_PROCESSED), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(facility1)));

        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.batches()).thenReturn(batchService);

        ArgumentCaptor<BatchCreateParams> paramsCaptor = ArgumentCaptor.forClass(BatchCreateParams.class);
        when(batchService.create(paramsCaptor.capture())).thenReturn(endedBatch("batch_test", 1));
        when(batchService.retrieve(anyString())).thenReturn(endedBatch("batch_test", 1));

        StreamResponse<MessageBatchIndividualResponse> emptyStream = mockEmptyStream();
        when(batchService.resultsStreaming(anyString())).thenReturn(emptyStream);

        FacilityConditionLlmBatchApiResult result = facilityConditionLlmBatchApiService.run(1);

        assertThat(result.getSubmitted()).isEqualTo(1);
        assertThat(paramsCaptor.getValue().requests()).hasSize(1);
    }

    private StreamResponse<MessageBatchIndividualResponse> mockEmptyStream() {
        @SuppressWarnings("unchecked")
        StreamResponse<MessageBatchIndividualResponse> stream = org.mockito.Mockito.mock(StreamResponse.class);
        when(stream.stream()).thenReturn(java.util.stream.Stream.empty());
        return stream;
    }

    private MessageBatch endedBatch(
            String id,
            long succeeded
    ) {
        return MessageBatch.builder()
                .id(id)
                .processingStatus(MessageBatch.ProcessingStatus.ENDED)
                .requestCounts(MessageBatchRequestCounts.builder()
                        .processing(0)
                        .succeeded(succeeded)
                        .errored(0)
                        .canceled(0)
                        .expired(0)
                        .build())
                .createdAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .archivedAt(Optional.empty())
                .cancelInitiatedAt(Optional.empty())
                .endedAt(Optional.of(OffsetDateTime.now()))
                .resultsUrl(Optional.empty())
                .build();
    }

    private Facility facility(Long facilityId) {
        return facility(facilityId, "전 견종 동반 가능");
    }

    private Facility facility(
            Long facilityId,
            String allowedAnimalText
    ) {
        Facility facility = Facility.builder()
                .name("테스트 시설")
                .category(FacilityCategory.CAFE)
                .petAllowed(PetAllowed.ALLOWED)
                .allowedAnimalText(allowedAnimalText)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

    /** 관광공사 원문은 전부 비어 있고 정리 안내문만 있는 시설 — 실측 8,600건이 이 모양이다. */
    private Facility facilityWithConditionRaw(
            Long facilityId,
            String petConditionRaw
    ) {
        Facility facility = Facility.builder()
                .name("테스트 시설")
                .category(FacilityCategory.CAFE)
                .petAllowed(PetAllowed.ALLOWED)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        org.springframework.test.util.ReflectionTestUtils.setField(facility, "petConditionRaw", petConditionRaw);
        return facility;
    }
}
