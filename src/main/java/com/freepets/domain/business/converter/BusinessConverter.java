package com.freepets.domain.business.converter;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;

import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.business.entity.RequestedCondition;
import com.freepets.domain.facility.entity.CheckList;
import com.freepets.domain.facility.entity.Confidence;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.report.entity.FacilityReport;
import com.freepets.domain.report.repository.DowngradingDenialReport;
import com.freepets.infra.nts.NtsValidationResult;

public class BusinessConverter {

    private BusinessConverter() {}

    public static BusinessResponseDTO.VerifyResult toVerifyResult(NtsValidationResult result) {
        return new BusinessResponseDTO.VerifyResult(
                result.valid(),
                result.statusCode(),
                result.statusLabel()
        );
    }

    public static BusinessResponseDTO.ClaimResult toClaimResult(FacilityOwnerClaim claim) {
        return new BusinessResponseDTO.ClaimResult(
                claim.getClaimId(),
                claim.getFacility().getFacilityId(),
                claim.getStatus()
        );
    }

    public static BusinessResponseDTO.MyClaimList toMyClaimList(List<FacilityOwnerClaim> claims) {
        return new BusinessResponseDTO.MyClaimList(
                claims.stream().map(BusinessConverter::toMyClaim).toList()
        );
    }

    private static BusinessResponseDTO.MyClaim toMyClaim(FacilityOwnerClaim claim) {
        Facility facility = claim.getFacility();
        return new BusinessResponseDTO.MyClaim(
                claim.getClaimId(),
                facility.getFacilityId(),
                facility.getName(),
                facility.getAddress(),
                claim.getStatus(),
                claim.getCreatedAt()
        );
    }

    /**
     * 내 매장 목록. 신뢰도는 저장값이 아니라 여기서 계산한다({@link Confidence#of}) — 시설 상세
     * ({@code FacilityConverter.toFacilityDetail})와 같은 규칙을 써야 사장님이 보는 배지와 손님이
     * 보는 배지가 어긋나지 않는다.
     *
     * @param denialAlertCounts    시설별 신뢰도를 내리고 있는 거부 제보 수. 제보가 없는 시설은 키가 없다
     * @param latestDenialAlerts   시설별 가장 최근 거부 제보. 제보가 없는 시설은 키가 없다
     * @param weeklyPetCheckCounts 시설별 이번 주 판별 수. 판별이 없는 시설은 키가 없다
     */
    public static BusinessResponseDTO.OwnerFacilityList toOwnerFacilityList(
            List<FacilityOwnerClaim> claims,
            Map<Long, Long> denialAlertCounts,
            Map<Long, DowngradingDenialReport> latestDenialAlerts,
            Map<Long, Long> weeklyPetCheckCounts
    ) {
        List<BusinessResponseDTO.OwnerFacility> facilities = claims.stream()
                .map(FacilityOwnerClaim::getFacility)
                .map(facility -> toOwnerFacility(
                        facility,
                        denialAlertCounts.getOrDefault(facility.getFacilityId(), 0L),
                        latestDenialAlerts.get(facility.getFacilityId()),
                        weeklyPetCheckCounts.getOrDefault(facility.getFacilityId(), 0L)
                ))
                .toList();

        return new BusinessResponseDTO.OwnerFacilityList(facilities);
    }

    private static BusinessResponseDTO.OwnerFacility toOwnerFacility(
            Facility facility,
            long denialAlertCount,
            DowngradingDenialReport latestDenialAlert,
            long weeklyPetCheckCount
    ) {
        return new BusinessResponseDTO.OwnerFacility(
                facility.getFacilityId(),
                facility.getName(),
                facility.getCategory(),
                facility.getAddress(),
                toEntryCondition(facility, denialAlertCount),
                new BusinessResponseDTO.Stats(weeklyPetCheckCount, facility.getReviewCount()),
                toDenialAlerts(denialAlertCount, latestDenialAlert),
                toFacilityProfile(facility)
        );
    }

    public static BusinessResponseDTO.FacilityProfile toFacilityProfile(Facility facility) {
        return new BusinessResponseDTO.FacilityProfile(
                facility.getIntroduction(),
                facility.getAmenityTags()
        );
    }

    public static BusinessResponseDTO.EntryCondition toEntryCondition(
            Facility facility,
            long denialAlertCount
    ) {
        Confidence.View confidence = Confidence.of(
                facility.getPetConditionRaw(),
                denialAlertCount,
                facility.getConfirmedAt()
        );

        List<Requirement> requirements = facility.getCheckLists().stream()
                .map(CheckList::getType)
                .toList();

        return new BusinessResponseDTO.EntryCondition(
                facility.getPetAllowed(),
                facility.getMaxWeight(),
                facility.getMaxWeightInclusive(),
                requirements,
                facility.getPetConditionRaw(),
                facility.getConfirmedAt(),
                confidence.confidence(),
                confidence.source()
        );
    }

    /**
     * 제보가 없으면 최신 제보를 비운다 — 경고 카드 자체를 그리지 않는다. 건수와 최신 제보는 서로 다른
     * 쿼리에서 오므로 둘 중 하나만 비는 상태가 나올 수 있어, 건수를 기준으로 맞춘다.
     */
    private static BusinessResponseDTO.DenialAlerts toDenialAlerts(
            long denialAlertCount,
            DowngradingDenialReport latestDenialAlert
    ) {
        if (denialAlertCount == 0 || latestDenialAlert == null) {
            return new BusinessResponseDTO.DenialAlerts(0, null);
        }

        return new BusinessResponseDTO.DenialAlerts(
                denialAlertCount,
                new BusinessResponseDTO.DenialAlert(
                        latestDenialAlert.denialReason(),
                        latestDenialAlert.reportedAt()
                )
        );
    }

    public static BusinessResponseDTO.AdminClaimList toAdminClaimList(
            Page<FacilityOwnerClaim> claimPage,
            Set<Long> approvedFacilityIds
    ) {
        List<BusinessResponseDTO.AdminClaim> claims = claimPage.getContent().stream()
                .map(claim -> toAdminClaim(claim, approvedFacilityIds))
                .toList();

        return new BusinessResponseDTO.AdminClaimList(
                claims,
                new BusinessResponseDTO.PageInfo(
                        claimPage.getNumber(),
                        claimPage.getSize(),
                        claimPage.getTotalElements(),
                        claimPage.hasNext()
                )
        );
    }

    private static BusinessResponseDTO.AdminClaim toAdminClaim(
            FacilityOwnerClaim claim,
            Set<Long> approvedFacilityIds
    ) {
        Facility facility = claim.getFacility();
        // 4단계(신청 접수) 이전에 곧바로 승인 상태로 만들어진 기존 매장은 이 값을 저장한 적이 없어
        // null이다 — Hibernate는 @Embedded 컬럼이 전부 null이면 필드 자체를 null로 돌려준다.
        RequestedCondition condition = claim.getRequestedCondition();
        return new BusinessResponseDTO.AdminClaim(
                claim.getClaimId(),
                facility.getFacilityId(),
                facility.getName(),
                facility.getAddress(),
                claim.getUser().getId(),
                claim.getUser().getNickname(),
                claim.getMaskedBusinessNumber(),
                claim.getVerifiedAt(),
                condition == null ? null : condition.getPetAllowed(),
                condition == null ? null : condition.getMaxWeight(),
                condition == null ? null : condition.getMaxWeightInclusive(),
                condition == null ? List.of() : condition.getRequirements(),
                condition == null ? null : condition.getConditionRaw(),
                claim.getRegistrationCertificateUrl(),
                claim.getStatus(),
                claim.getCreatedAt(),
                claim.getReviewedAt(),
                claim.getReviewReason(),
                approvedFacilityIds.contains(facility.getFacilityId())
        );
    }

    public static BusinessResponseDTO.DenialAlertList toDenialAlertList(List<FacilityReport> reports) {
        return new BusinessResponseDTO.DenialAlertList(
                reports.stream().map(BusinessConverter::toDenialAlertDetail).toList()
        );
    }

    private static BusinessResponseDTO.DenialAlertDetail toDenialAlertDetail(FacilityReport report) {
        return new BusinessResponseDTO.DenialAlertDetail(
                report.getReportId(),
                report.getDenialReason(),
                report.getContent(),
                report.getCreatedAt()
        );
    }

    public static BusinessResponseDTO.AdminClaimActionResult toAdminClaimActionResult(FacilityOwnerClaim claim) {
        return new BusinessResponseDTO.AdminClaimActionResult(
                claim.getClaimId(),
                claim.getFacility().getFacilityId(),
                claim.getStatus()
        );
    }
}
