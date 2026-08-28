package com.freepets.domain.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.PetConditionStatus;
import com.freepets.domain.facility.repository.FacilityRepository;

@ExtendWith(MockitoExtension.class)
class FacilityConditionLlmBatchServiceTest {

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private FacilityConditionLlmParser facilityConditionLlmParser;

    @InjectMocks
    private FacilityConditionLlmBatchService facilityConditionLlmBatchService;

    @Test
    void NOT_PROCESSED이_없으면_바로_끝난다() {
        when(facilityRepository.findByPetConditionStatus(eq(PetConditionStatus.NOT_PROCESSED), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of()));

        FacilityConditionLlmBatchResult result = facilityConditionLlmBatchService.parseAll();

        assertThat(result.getProcessed()).isZero();
    }

    @Test
    void 페이지에_있는_시설을_전부_파싱하고_저장한다() {
        Facility facility1 = facility(1L);
        Facility facility2 = facility(2L);

        when(facilityRepository.findByPetConditionStatus(eq(PetConditionStatus.NOT_PROCESSED), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(facility1, facility2)))
                .thenReturn(new SliceImpl<>(List.of()));

        when(facilityConditionLlmParser.parse(any(), any(), any(), any(), any()))
                .thenReturn(FacilityConditionLlmParseResult.noCondition());

        FacilityConditionLlmBatchResult result = facilityConditionLlmBatchService.parseAll();

        assertThat(result.getProcessed()).isEqualTo(2);
        assertThat(result.countOf(PetConditionStatus.NO_CONDITION)).isEqualTo(2);
        verify(facilityRepository, times(2)).save(any(Facility.class));
    }

    @Test
    void 파싱이_실패한_시설은_건너뛰고_계속_진행한다() {
        Facility ok = facility(1L);
        Facility broken = facility(2L);

        when(facilityRepository.findByPetConditionStatus(eq(PetConditionStatus.NOT_PROCESSED), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(ok, broken)))
                .thenReturn(new SliceImpl<>(List.of()));

        when(facilityConditionLlmParser.parse(any(), any(), any(), any(), any()))
                .thenReturn(FacilityConditionLlmParseResult.fromExtraction(
                        new FacilityConditionExtraction(new BigDecimal("10.0"), false, List.of(), null, null)
                ))
                .thenThrow(new RuntimeException("Claude 호출 실패"));

        FacilityConditionLlmBatchResult result = facilityConditionLlmBatchService.parseAll();

        assertThat(result.getProcessed()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        verify(facilityRepository, times(1)).save(any(Facility.class));
    }

    @Test
    void 규칙_엔진이_이미_maxWeight를_뽑아낸_시설도_LLM은_호출하되_maxWeight는_규칙_엔진_값을_유지한다() {
        // CodeRabbit 리뷰 지적 — maxWeight 하나 뽑았다고 LLM을 통째로 건너뛰면, 같은 원문에 같이
        // 있을 수 있는 맹견 배제/구역 제한 같은 다른 조건이 영영 사라진다. LLM은 항상 부르되
        // maxWeight만 규칙 엔진 값으로 덮어써야 한다.
        Facility alreadyHasWeight = facility(1L);
        org.springframework.test.util.ReflectionTestUtils.setField(
                alreadyHasWeight, "maxWeight", new BigDecimal("10.0")
        );

        when(facilityRepository.findByPetConditionStatus(eq(PetConditionStatus.NOT_PROCESSED), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(alreadyHasWeight)))
                .thenReturn(new SliceImpl<>(List.of()));

        // LLM은 (규칙 엔진이 못 보는) 맹견 배제를 찾아내지만, maxWeight는 임의로 다르게 읽었다고 가정.
        when(facilityConditionLlmParser.parse(any(), any(), any(), any(), any()))
                .thenReturn(FacilityConditionLlmParseResult.fromExtraction(
                        new FacilityConditionExtraction(new BigDecimal("999.0"), true, List.of(), null, null)
                ));

        FacilityConditionLlmBatchResult result = facilityConditionLlmBatchService.parseAll();

        assertThat(result.getProcessed()).isEqualTo(1);
        verify(facilityConditionLlmParser, times(1)).parse(any(), any(), any(), any(), any());
        assertThat(alreadyHasWeight.getMaxWeight()).isEqualByComparingTo(new BigDecimal("10.0"));
        assertThat(alreadyHasWeight.isDangerousBreedExcluded()).isTrue();
    }

    @Test
    void 페이지_전체가_실패하면_무한루프_없이_중단한다() {
        Facility broken = facility(1L);

        when(facilityRepository.findByPetConditionStatus(eq(PetConditionStatus.NOT_PROCESSED), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(broken)));

        when(facilityConditionLlmParser.parse(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Claude 호출 실패"));

        FacilityConditionLlmBatchResult result = facilityConditionLlmBatchService.parseAll();

        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getProcessed()).isZero();
        // 무한 루프였다면 이 테스트 자체가 끝나지 않았을 것 — 종료된다는 사실 자체가 검증.
    }

    private Facility facility(Long facilityId) {
        Facility facility = Facility.builder()
                .name("테스트 시설")
                .category(FacilityCategory.CAFE)
                .petAllowed(PetAllowed.ALLOWED)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }
}
