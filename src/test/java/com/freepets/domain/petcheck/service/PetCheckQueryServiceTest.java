package com.freepets.domain.petcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.petcheck.dto.PetCheckResponseDTO;
import com.freepets.domain.petcheck.entity.PetCheck;
import com.freepets.domain.petcheck.entity.PetCheckResult;
import com.freepets.domain.petcheck.entity.PetCheckVerdict;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.petcheck.repository.PetCheckVerdictRepository;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.global.util.JsonListUtil;

@ExtendWith(MockitoExtension.class)
class PetCheckQueryServiceTest {

    @Mock
    private PetCheckRepository petCheckRepository;

    @Mock
    private PetCheckVerdictRepository petCheckVerdictRepository;

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

    @Test
    void 존재하지_않는_검증_코드로_조회하면_예외() {
        when(petCheckVerdictRepository.findByVerifyCode("FP-NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> petCheckQueryService.getVerifyPage("FP-NOPE"))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 정상_코드면_시설_반려동물_조건을_검증_페이지_데이터로_변환한다() {
        Facility facility = Facility.builder()
                .name("테라로자 커피공장")
                .category(FacilityCategory.CAFE)
                .petAllowed(PetAllowed.ALLOWED)
                .build();
        PetCheck petCheck = PetCheck.builder()
                .facility(facility)
                .overall(PetCheckResult.CONDITIONAL)
                .build();
        Pet pet = Pet.builder()
                .name("몽이")
                .kind(Kind.DOG)
                .species("말티즈")
                .weight(new BigDecimal("3.20"))
                .breedSize(BreedSize.SMALL)
                .isVaccinated(true)
                .build();
        PetCheckVerdict verdict = PetCheckVerdict.builder()
                .pet(pet)
                .result(PetCheckResult.CONDITIONAL)
                .reason("몽이는 리드줄만 착용하면 이용 가능합니다")
                .conditions(JsonListUtil.toJson(List.of("리드줄 필수 착용")))
                .verifyCode("FP-ABC1234567")
                .build();
        petCheck.addVerdict(verdict);

        when(petCheckVerdictRepository.findByVerifyCode("FP-ABC1234567")).thenReturn(Optional.of(verdict));

        PetCheckResponseDTO.VerifyPage page = petCheckQueryService.getVerifyPage("FP-ABC1234567");

        assertThat(page.facilityName()).isEqualTo("테라로자 커피공장");
        assertThat(page.result()).isEqualTo(PetCheckResult.CONDITIONAL);
        assertThat(page.pet().name()).isEqualTo("몽이");
        assertThat(page.pet().breedSizeLabel()).isEqualTo("소형견");
        assertThat(page.conditions()).containsExactly("리드줄 필수 착용");
    }

    @Test
    void 반려동물이_삭제됐으면_pet이_null인_채로_나머지는_반환한다() {
        Facility facility = Facility.builder()
                .name("테라로자 커피공장")
                .category(FacilityCategory.CAFE)
                .petAllowed(PetAllowed.ALLOWED)
                .build();
        PetCheck petCheck = PetCheck.builder()
                .facility(facility)
                .overall(PetCheckResult.ALLOWED)
                .build();
        // pet(null) — 반려동물 삭제 시 fk_verdict_pet ON DELETE SET NULL로 이렇게 남는다.
        PetCheckVerdict verdict = PetCheckVerdict.builder()
                .result(PetCheckResult.ALLOWED)
                .reason("모든 조건을 충족해 출입 가능합니다")
                .conditions(JsonListUtil.toJson(List.of()))
                .verifyCode("FP-DEF1234567")
                .build();
        petCheck.addVerdict(verdict);

        when(petCheckVerdictRepository.findByVerifyCode("FP-DEF1234567")).thenReturn(Optional.of(verdict));

        PetCheckResponseDTO.VerifyPage page = petCheckQueryService.getVerifyPage("FP-DEF1234567");

        assertThat(page.pet()).isNull();
        assertThat(page.facilityName()).isEqualTo("테라로자 커피공장");
    }
}
