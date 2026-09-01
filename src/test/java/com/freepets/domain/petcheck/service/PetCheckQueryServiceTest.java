package com.freepets.domain.petcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.freepets.domain.petcheck.dto.PetCheckResponseDTO;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class PetCheckQueryServiceTest {

    @Mock
    private PetCheckRepository petCheckRepository;

    @InjectMocks
    private PetCheckQueryService petCheckQueryService;

    @Test
    void limit이_0이하면_예외() {
        assertThatThrownBy(() -> petCheckQueryService.getMyChecks(1L, null, 0, 0))
                .isInstanceOf(GeneralException.class);
        verifyNoInteractions(petCheckRepository);
    }

    @Test
    void limit이_상한을_넘으면_예외() {
        // 상한(100)이 없으면 limit을 크게 보내는 요청 하나로 DB가 한 번에 큰 결과 집합을
        // 만들게 할 수 있다 — FacilityRequestDTO의 페이지 크기 상한과 동일하게 맞춘다.
        assertThatThrownBy(() -> petCheckQueryService.getMyChecks(1L, null, 101, 0))
                .isInstanceOf(GeneralException.class);
        verifyNoInteractions(petCheckRepository);
    }

    @Test
    void offset이_음수면_예외() {
        assertThatThrownBy(() -> petCheckQueryService.getMyChecks(1L, null, 20, -1))
                .isInstanceOf(GeneralException.class);
        verifyNoInteractions(petCheckRepository);
    }

    @Test
    void offset이_limit의_배수가_아니면_예외() {
        // offset/limit 정수 나눗셈이 조용히 다른 페이지로 내림 계산되는 걸 막는다 — 어긋나면
        // 호출자가 모르는 채로 엉뚱한 페이지를 받는 대신 명시적으로 400을 낸다.
        assertThatThrownBy(() -> petCheckQueryService.getMyChecks(1L, null, 20, 15))
                .isInstanceOf(GeneralException.class);
        verifyNoInteractions(petCheckRepository);
    }

    @Test
    void 정상_범위면_offset을_limit으로_나눈_페이지로_조회한다() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(petCheckRepository.findAllByUser_IdOrderByCreatedAtDesc(eq(1L), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        PetCheckResponseDTO.CheckHistoryList result = petCheckQueryService.getMyChecks(1L, null, 20, 40);

        assertThat(result.total()).isZero();
        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(2, 20));
    }

    @Test
    void facilityId가_있으면_시설_한정_조회를_쓴다() {
        when(petCheckRepository.findAllByUser_IdAndFacility_FacilityIdOrderByCreatedAtDesc(eq(1L), eq(7L), any()))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        petCheckQueryService.getMyChecks(1L, 7L, 20, 0);

        verify(petCheckRepository).findAllByUser_IdAndFacility_FacilityIdOrderByCreatedAtDesc(eq(1L), eq(7L), any());
    }
}
