package com.freepets.domain.business.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.freepets.domain.business.entity.ClaimStatus;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;

public class BusinessResponseDTO {

    private BusinessResponseDTO() {}

    /**
     * @param valid       진위확인 통과 여부. 불일치는 4xx로 내려가므로 200 응답에서는 항상 {@code true}다
     * @param status      사업 상태 코드. {@code 01} 계속사업자 / {@code 02} 휴업자 / {@code 03} 폐업자
     * @param statusLabel 사업 상태 문구. 화면이 사유를 그대로 보여줄 수 있게 함께 내린다
     */
    public record VerifyResult(
            boolean valid,
            String status,
            String statusLabel
    ) {}

    /**
     * 매장 등록 신청 접수 결과. 접수일 뿐이라 아직 소유권도 사업자 프로필도 생기지 않는다 — 앱은 대시보드가
     * 아니라 "심사 중" 화면으로 가야 한다. 운영자가 승인해야 조건이 시설에 반영되고 프로필이 붙는다.
     *
     * @param status 접수 직후라 항상 {@code PENDING}이다
     */
    public record ClaimResult(
            Long claimId,
            Long facilityId,
            ClaimStatus status
    ) {}

    /**
     * 내 매장 등록 신청 목록. 상태와 무관하게 전부 최신순으로 내려간다 — 지난 반려 이력도 화면에서 보여줄 수 있다.
     */
    public record MyClaimList(
            List<MyClaim> claims
    ) {}

    /**
     * @param appliedAt 신청을 접수한 시각
     */
    public record MyClaim(
            Long claimId,
            Long facilityId,
            String facilityName,
            String facilityAddress,
            ClaimStatus status,
            LocalDateTime appliedAt
    ) {}

    /**
     * 관리자 심사 목록. 오래된 신청부터 보여준다({@code pageInfo}는 그 순서를 따라간다) — 내 신청 목록과
     * 달리 큐를 처리하는 화면이라 최신순이 아니다.
     */
    public record AdminClaimList(
            List<AdminClaim> claims,
            PageInfo pageInfo
    ) {}

    /**
     * 관리자 심사용 신청 상세. 등록증 URL을 그대로 내려준다({@code S3ImageService.uploadDocument} 참고) —
     * 자영업자용 응답({@link MyClaim})에는 없는 민감 정보라 이 응답에만 담는다.
     *
     * @param hasApprovedOwner 같은 매장에 이미 승인된 소유자가 있는지. 승인하려는 매장이면 이 값이
     *                         {@code true}일 때 먼저 그 소유자와의 관계를 확인해야 한다 — 같은 매장의
     *                         다른 대기 신청을 승인 시 자동 반려하지 않기로 했다
     * @param appliedAt        신청을 접수한 시각
     * @param reviewedAt       관리자가 심사한 시각. 아직 대기 중이면 {@code null}
     * @param reviewReason     반려·해제 사유. 승인됐거나 아직 대기 중이면 {@code null}
     */
    public record AdminClaim(
            Long claimId,
            Long facilityId,
            String facilityName,
            String facilityAddress,
            Long applicantUserId,
            String applicantNickname,
            String maskedBusinessNumber,
            LocalDateTime verifiedAt,
            PetAllowed requestedPetAllowed,
            BigDecimal requestedMaxWeight,
            Boolean requestedMaxWeightInclusive,
            List<Requirement> requestedRequirements,
            String requestedConditionRaw,
            String registrationCertificateUrl,
            ClaimStatus status,
            LocalDateTime appliedAt,
            LocalDateTime reviewedAt,
            String reviewReason,
            boolean hasApprovedOwner
    ) {}

    public record AdminClaimActionResult(
            Long claimId,
            Long facilityId,
            ClaimStatus status
    ) {}

    public record PageInfo(
            int page,
            int size,
            long totalElements,
            boolean hasNext
    ) {}
}
