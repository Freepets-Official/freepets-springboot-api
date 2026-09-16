package com.freepets.domain.business.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.freepets.domain.business.entity.ClaimStatus;
import com.freepets.domain.facility.entity.Confidence;
import com.freepets.domain.facility.entity.ConfidenceSource;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.report.entity.DenialReason;

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

    /**
     * 사업자 대시보드 첫 화면 — 내 매장 목록. 소유 기록이 생긴 순서대로 내려간다.
     *
     * <p>계정 조회({@code GET /users/account})의 {@code ownedFacilityIds}만으로는 카드를 그릴 수 없어
     * 이름·주소·현재 조건까지 함께 준다.
     */
    public record OwnerFacilityList(
            List<OwnerFacility> facilities
    ) {}

    /**
     * 매장 카드 한 장. 사장님 본인에게 내려가는 응답이라 조건 값을 숨기지 않고 그대로 보여준다 —
     * 여기서 바로 조건 수정 화면으로 넘어간다.
     *
     * <p>홈 화면이 카드와 거부 제보 경고를 한꺼번에 그리므로 지표와 제보 요약을 이 응답에 함께 싣는다.
     * 매장마다 따로 부르게 하면 매장이 늘어날수록 홈 진입이 느려진다.
     *
     * <p>화면의 블록대로 나눠 담는다 — 매장 소개·홍보나 방문 혜택이 붙을 때 최상위가 계속 넓어지지
     * 않도록 자리를 만들어 둔다.
     */
    public record OwnerFacility(
            Long facilityId,
            String name,
            FacilityCategory category,
            String address,
            EntryCondition entryCondition,
            Stats stats,
            DenialAlerts denialAlerts
    ) {}

    /**
     * 지금 이 매장에 걸려 있는 출입 조건. 조건 수정 화면이 이 값을 그대로 초기값으로 쓴다.
     *
     * @param confirmedAt      조건을 확정한 시각. 확정한 적이 없으면 {@code null}
     * @param confidence       조회 시점에 계산한 신뢰도. 저장값이 아니다({@code Confidence#of})
     * @param confidenceSource 신뢰도의 근거. 확정 이후 거부 제보가 들어왔다면 {@code DENIAL_REPORT}가 되어
     *                         {@code confirmedAt}이 차 있어도 확정 배지가 아니다
     */
    public record EntryCondition(
            PetAllowed petAllowed,
            BigDecimal maxWeight,
            Boolean maxWeightInclusive,
            List<Requirement> requirements,
            String conditionRaw,
            LocalDateTime confirmedAt,
            Confidence confidence,
            ConfidenceSource confidenceSource
    ) {}

    /**
     * 카드에 숫자로 찍히는 지표. 동반 여부는 {@link EntryCondition#petAllowed()}를 그대로 읽는다 —
     * 같은 값을 두 군데 담으면 한쪽만 고쳐질 수 있다.
     *
     * @param weeklyPetCheckCount 이번 주(KST 월요일 00:00부터) 이 매장을 판별한 <b>횟수</b>.
     *                            같은 사람이 여러 번 판별하면 그만큼 올라간다
     * @param reviewCount         등급 산정에 쓰인 리뷰 수. 승인된 신고가 달린 리뷰는 빠진 값이다
     */
    public record Stats(
            long weeklyPetCheckCount,
            long reviewCount
    ) {}

    /**
     * 홈의 거부 제보 경고 카드.
     *
     * @param count  지금 신뢰도를 내리고 있는 거부 제보 수. 배지와 같은 기준으로 세므로 이 값이 0보다
     *               크면 {@link EntryCondition#confidence()}는 항상 확정이 아니다
     * @param latest 그중 가장 최근 제보. {@code count}가 0이면 {@code null}이고 경고 카드를 그리지 않는다
     */
    public record DenialAlerts(
            long count,
            DenialAlert latest
    ) {}

    /**
     * 경고 카드가 보여줄 제보 한 줄. 제보자를 특정할 수 있는 값은 담지 않는다 — 사장님이 제보자를
     * 알아낼 수 있으면 거부 제보 자체가 위축된다.
     *
     * <p>표시 문구는 프론트가 만든다({@code DenialReason}에 라벨이 있다). 소비자용
     * {@code DenialReportResponseDTO.AlertReport}도 같은 방식이다.
     */
    public record DenialAlert(
            DenialReason reason,
            LocalDateTime reportedAt
    ) {}

    public record PageInfo(
            int page,
            int size,
            long totalElements,
            boolean hasNext
    ) {}
}
