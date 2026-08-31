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
    void 대상_시설_수만큼_배치_요청을_만들어_제출한다() {
        Facility facility1 = facility(1L);
        Facility facility2 = facility(2L);

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
                .containsExactlyInAnyOrder("1", "2");
        verify(batchService).create(any(BatchCreateParams.class));
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
        Facility facility = Facility.builder()
                .name("테스트 시설")
                .category(FacilityCategory.CAFE)
                .petAllowed(PetAllowed.ALLOWED)
                .allowedAnimalText("전 견종 동반 가능")
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }
}
