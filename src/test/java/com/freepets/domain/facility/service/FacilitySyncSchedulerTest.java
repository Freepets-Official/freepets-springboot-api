package com.freepets.domain.facility.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// @ConditionalOnProperty/@Scheduled는 스프링 컨텍스트 관련 설정이라 여기서 검증하지 않는다
// (기본 비활성 여부는 스프링 자체 메커니즘이라 별도 확인 불필요). syncAndParse()가 두 서비스를
// 순서대로(동기화 먼저, 파싱 나중) 호출하는지만 검증한다.
@ExtendWith(MockitoExtension.class)
class FacilitySyncSchedulerTest {

    @Mock
    private FacilitySyncService facilitySyncService;

    @Mock
    private FacilityConditionLlmBatchService facilityConditionLlmBatchService;

    @InjectMocks
    private FacilitySyncScheduler facilitySyncScheduler;

    @Test
    void 동기화_먼저_파싱_나중_순서로_호출한다() {
        when(facilitySyncService.syncAll()).thenReturn(new FacilitySyncResult());
        when(facilityConditionLlmBatchService.parseAll()).thenReturn(new FacilityConditionLlmBatchResult());

        facilitySyncScheduler.syncAndParse();

        InOrder inOrder = inOrder(facilitySyncService, facilityConditionLlmBatchService);
        inOrder.verify(facilitySyncService).syncAll();
        inOrder.verify(facilityConditionLlmBatchService).parseAll();
    }
}
