package com.freepets.domain.business.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.business.converter.BusinessConverter;
import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.business.entity.RequestedCondition;
import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매장 등록 신청 접수. DB 작업만 맡고 국세청 확인과 등록증 업로드는 {@link BusinessCommandService}가
 * 트랜잭션 밖에서 끝내둔다 — 외부 API 응답을 트랜잭션 안에서 기다리면 그동안 커넥션을 붙잡는다.
 *
 * <p>신청은 운영자 승인을 기다리는 대기 상태로만 만든다. <b>시설 조건은 건드리지 않는다</b> — 신청서에 담아뒀다가
 * 승인할 때 반영한다. 소유권과 사업자 프로필도 승인된 기록에서만 파생한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FacilityOwnerClaimCommandService {

    // "시설당 승인된 사업자 하나" 조건부 유니크 인덱스. JPA로 표현할 수 없어 DB에 직접 건다
    // (db/pending-manual-migrations.sql). PostgreSQL은 유니크 인덱스 위반도 인덱스 이름을 제약 이름으로 알려준다.
    private static final String APPROVED_FACILITY_UNIQUE_INDEX = "uk_facility_owner_claims_approved_facility";

    // "같은 사람이 같은 매장에 대기 신청 하나" 조건부 유니크 인덱스.
    private static final String PENDING_USER_FACILITY_UNIQUE_INDEX = "uk_facility_owner_claims_pending_user_facility";

    // 수동 마이그레이션 전까지 DB에 남아 있는 옛 "시설당 한 행" 제약. 그 사이 충돌이 나면 이 이름으로 올라오므로
    // 함께 409로 바꾼다 — 빠뜨리면 중복 신청이 500으로 나간다. 마이그레이션을 적용하면 지운다.
    private static final String LEGACY_FACILITY_UNIQUE_CONSTRAINT = "uk_facility_owner_claims_facility_id";

    private static final Map<String, ErrorStatus> CONSTRAINT_ERROR_STATUSES = Map.of(
            APPROVED_FACILITY_UNIQUE_INDEX, ErrorStatus.BUSINESS4003,
            LEGACY_FACILITY_UNIQUE_CONSTRAINT, ErrorStatus.BUSINESS4003,
            PENDING_USER_FACILITY_UNIQUE_INDEX, ErrorStatus.BUSINESS4004
    );

    private final FacilityOwnerClaimRepository facilityOwnerClaimRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;

    /**
     * 신청할 수 있는 상태인지 미리 확인한다. 국세청 호출과 등록증 업로드 전에 불러서, 어차피 막힐 요청에
     * 외부 호출과 파일 업로드를 쓰지 않게 한다. 확인과 저장 사이는 {@link #apply}가 시설 행을 잠그고 다시 막는다.
     */
    @Transactional(readOnly = true)
    public void validateApplicable(
            Long userId,
            Long facilityId
    ) {
        if (!facilityRepository.existsById(facilityId)) {
            throw new GeneralException(ErrorStatus.FACILITY4001);
        }

        requireApplicable(userId, facilityId);
    }

    /**
     * 신청을 접수한다. 대기 상태 기록만 만들고 시설은 건드리지 않는다.
     */
    public BusinessResponseDTO.ClaimResult apply(
            Long userId,
            Long facilityId,
            String maskedBusinessNumber,
            LocalDateTime verifiedAt,
            String registrationCertificateUrl,
            BusinessRequestDTO.ClaimRequest request
    ) {
        // 같은 시설에 동시에 들어온 신청을 행 단위로 직렬화한다. 잠금 없이 "승인된 주인이 있는지" 확인하고
        // 저장하면 거의 동시에 들어온 두 요청이 둘 다 그 확인을 통과한다.
        Facility facility = facilityRepository.findByIdForUpdate(facilityId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4001));

        requireApplicable(userId, facilityId);

        FacilityOwnerClaim claim = saveClaim(FacilityOwnerClaim.builder()
                .user(findUser(userId))
                .facility(facility)
                .maskedBusinessNumber(maskedBusinessNumber)
                .verifiedAt(verifiedAt)
                .requestedCondition(RequestedCondition.of(
                        request.getPetAllowed(),
                        request.getMaxWeight(),
                        request.getMaxWeightInclusive(),
                        request.getRequirements(),
                        request.getConditionRaw()
                ))
                .registrationCertificateUrl(registrationCertificateUrl)
                .build());

        return BusinessConverter.toClaimResult(claim);
    }

    /**
     * 이미 승인된 주인이 있으면 남의 매장이든 내 매장이든 신청을 받지 않는다. 내 매장이면 신청할 이유가 없고,
     * 승인된 매장의 조건 수정은 사업자 대시보드의 조건 수정 API가 맡는다.
     *
     * <p>본인이 낸 대기 신청이 있으면 같은 신청을 쌓지 않는다. 남이 낸 대기 신청은 막지 않는다 — 먼저 신청했다고
     * 진짜 사장을 막으면 승인 절차를 둔 의미가 없다.
     */
    private void requireApplicable(
            Long userId,
            Long facilityId
    ) {
        Optional<FacilityOwnerClaim> approvedClaim = facilityOwnerClaimRepository.findApprovedByFacilityId(facilityId);
        if (approvedClaim.isPresent()) {
            boolean isOwnedByRequester = approvedClaim.get().isRequestedBy(userId);
            log.info("이미 등록이 끝난 매장에 신청 시도: facilityId={}, userId={}, 본인 매장={}",
                    facilityId, userId, isOwnedByRequester);
            throw new GeneralException(isOwnedByRequester ? ErrorStatus.BUSINESS4005 : ErrorStatus.BUSINESS4003);
        }

        if (facilityOwnerClaimRepository.existsPendingByFacilityIdAndUserId(facilityId, userId)) {
            throw new GeneralException(ErrorStatus.BUSINESS4004);
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
    }

    // 신규 insert일 때는 FacilityOwnerClaim이 GenerationType.IDENTITY라 save() 호출 시점에 바로
    // INSERT가 나가서 여기서 제약 위반을 잡을 수 있다. 시퀀스 전략으로 바뀌면 flush가 커밋
    // 시점(이 메소드 밖)으로 밀려 이 catch가 더는 못 잡게 되니 주의.
    private FacilityOwnerClaim saveClaim(FacilityOwnerClaim claim) {
        try {
            return facilityOwnerClaimRepository.save(claim);
        } catch (DataIntegrityViolationException exception) {
            // 위의 행 잠금이 있어 도달하기 어렵지만, 잠금을 타지 않는 경로가 생겨도 DB 제약이
            // 마지막 방어선으로 남는다. 다만 FK·not-null 위반까지 409로 뭉뚱그리지 않도록
            // 아는 유니크 제약이 원인일 때만 바꾸고 나머지는 그대로 올린다.
            String constraintName = violatedConstraintName(exception);
            ErrorStatus errorStatus = CONSTRAINT_ERROR_STATUSES.get(constraintName);
            if (errorStatus == null) {
                throw exception;
            }

            log.warn("매장 등록 신청 중 유니크 제약({}) 충돌", constraintName, exception);
            throw new GeneralException(errorStatus);
        }
    }

    // getMostSpecificCause()는 원인 체인의 가장 아래(SQLException)까지 내려가버려서 중간에 있는
    // Hibernate의 ConstraintViolationException을 지나쳐버린다. 제약 이름은 그 예외가 들고 있으므로
    // 체인을 직접 순회하며 처음 만나는 걸 찾는다. 제약 이름을 못 찾으면 빈 문자열을 돌려준다 —
    // Map.of()로 만든 맵은 get(null)에 NullPointerException을 던진다.
    private String violatedConstraintName(DataIntegrityViolationException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                String constraintName = constraintViolation.getConstraintName();
                return constraintName == null ? "" : constraintName;
            }
        }
        return "";
    }
}
