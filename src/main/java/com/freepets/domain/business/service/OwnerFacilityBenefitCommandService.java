package com.freepets.domain.business.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.business.converter.BusinessConverter;
import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityBenefit;
import com.freepets.domain.facility.repository.FacilityBenefitRepository;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

/**
 * 사업자의 방문 혜택 추가·삭제·노출 토글. 판별에 쓰이지 않아 {@code confirmedAt}·추천 자격과 무관하고,
 * {@link OwnerFacilityProfileCommandService}처럼 비관적 락 없이 동작한다.
 *
 * <p>시설 ID를 인자로 받으므로 {@link FacilityOwnershipValidator}를 가장 먼저 호출한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OwnerFacilityBenefitCommandService {

    private final FacilityOwnershipValidator facilityOwnershipValidator;
    private final FacilityRepository facilityRepository;
    private final FacilityBenefitRepository facilityBenefitRepository;

    public BusinessResponseDTO.VisitBenefit createBenefit(
            Long userId,
            Long facilityId,
            BusinessRequestDTO.VisitBenefitCreateRequest request
    ) {
        facilityOwnershipValidator.requireOwner(userId, facilityId);

        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4001));

        FacilityBenefit benefit = FacilityBenefit.builder()
                .facility(facility)
                .title(request.getTitle())
                .description(request.getDescription())
                .build();
        facilityBenefitRepository.save(benefit);

        return BusinessConverter.toVisitBenefit(benefit);
    }

    public BusinessResponseDTO.VisitBenefitDeleteResult deleteBenefit(
            Long userId,
            Long facilityId,
            Long benefitId
    ) {
        facilityOwnershipValidator.requireOwner(userId, facilityId);

        FacilityBenefit benefit = findOwnedBenefit(facilityId, benefitId);
        facilityBenefitRepository.delete(benefit);

        return new BusinessResponseDTO.VisitBenefitDeleteResult(benefitId);
    }

    public BusinessResponseDTO.VisitBenefit updateEnabled(
            Long userId,
            Long facilityId,
            Long benefitId,
            BusinessRequestDTO.VisitBenefitEnabledUpdateRequest request
    ) {
        facilityOwnershipValidator.requireOwner(userId, facilityId);

        FacilityBenefit benefit = findOwnedBenefit(facilityId, benefitId);
        if (request.getIsEnabled()) {
            benefit.enable();
        } else {
            benefit.disable();
        }

        return BusinessConverter.toVisitBenefit(benefit);
    }

    private FacilityBenefit findOwnedBenefit(Long facilityId, Long benefitId) {
        return facilityBenefitRepository.findByFacilityBenefitIdAndFacility_FacilityId(benefitId, facilityId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BUSINESS4009));
    }
}
