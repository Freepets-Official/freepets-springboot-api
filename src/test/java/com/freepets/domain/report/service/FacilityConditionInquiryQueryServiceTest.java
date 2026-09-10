package com.freepets.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.report.dto.FacilityConditionInquiryResponseDTO;
import com.freepets.domain.report.repository.FacilityConditionInquiryRepository;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class FacilityConditionInquiryQueryServiceTest {

    @Mock
    private FacilityConditionInquiryRepository facilityConditionInquiryRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @InjectMocks
    private FacilityConditionInquiryQueryService facilityConditionInquiryQueryService;

    @Test
    void 시설의_누적_요청_수를_반환한다() {
        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(facilityConditionInquiryRepository.countByFacility_FacilityId(7L)).thenReturn(12L);

        FacilityConditionInquiryResponseDTO.CountResult result = facilityConditionInquiryQueryService.getCount(7L);

        assertThat(result.facilityId()).isEqualTo(7L);
        assertThat(result.count()).isEqualTo(12L);
    }

    @Test
    void 존재하지_않는_시설이면_FACILITY4001() {
        when(facilityRepository.existsById(7L)).thenReturn(false);

        assertThatThrownBy(() -> facilityConditionInquiryQueryService.getCount(7L))
                .isInstanceOf(GeneralException.class);
    }
}
