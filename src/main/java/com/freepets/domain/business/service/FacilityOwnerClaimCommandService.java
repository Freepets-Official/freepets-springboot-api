package com.freepets.domain.business.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.business.converter.BusinessConverter;
import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매장 소유 기록 생성과 출입 조건 확정. DB 작업만 맡고 국세청 확인은 {@link BusinessCommandService}가
 * 트랜잭션 밖에서 끝내둔다 — 외부 API 응답을 트랜잭션 안에서 기다리면 그동안 커넥션을 붙잡는다.
 *
 * <p>소유 기록이 생기는 순간 그 계정에 사업자 프로필이 붙는다(프로필은 저장하지 않고 이 기록에서 파생).
 * 소유권은 <b>승인된 기록만</b> 인정한다 — 심사 중이거나 반려·해제된 기록은 주인으로 보지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FacilityOwnerClaimCommandService {

    // "시설당 승인된 사업자 하나" 조건부 유니크 인덱스. JPA로 표현할 수 없어 DB에 직접 건다
    // (db/pending-manual-migrations.sql). PostgreSQL은 유니크 인덱스 위반도 인덱스 이름을 제약 이름으로 알려준다.
    private static final String APPROVED_FACILITY_UNIQUE_INDEX = "uk_facility_owner_claims_approved_facility";

    // 수동 마이그레이션 전까지 DB에 남아 있는 옛 "시설당 한 행" 제약. 그 사이 충돌이 나면 이 이름으로 올라오므로
    // 함께 409로 바꾼다 — 빠뜨리면 중복 등록이 500으로 나간다. 마이그레이션을 적용하면 지운다.
    private static final String LEGACY_FACILITY_UNIQUE_CONSTRAINT = "uk_facility_owner_claims_facility_id";

    private static final Set<String> FACILITY_UNIQUE_CONSTRAINTS = Set.of(
            APPROVED_FACILITY_UNIQUE_INDEX,
            LEGACY_FACILITY_UNIQUE_CONSTRAINT
    );

    private final FacilityOwnerClaimRepository facilityOwnerClaimRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;

    public BusinessResponseDTO.ClaimResult claim(
            Long userId,
            Long facilityId,
            String maskedBusinessNumber,
            LocalDateTime verifiedAt,
            BusinessRequestDTO.ClaimRequest request
    ) {
        // 같은 시설에 동시에 들어온 등록을 행 단위로 직렬화한다. 잠금 없이 "주인이 있는지" 확인하고
        // 저장하면 거의 동시에 들어온 두 요청이 둘 다 그 확인을 통과한다.
        Facility facility = facilityRepository.findByIdForUpdate(facilityId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4001));

        facilityOwnerClaimRepository.findApprovedByFacilityId(facilityId)
                .ifPresentOrElse(
                        existingClaim -> requireOwnedBy(existingClaim, facilityId, userId),
                        () -> createClaim(userId, facility, maskedBusinessNumber, verifiedAt)
                );

        facility.confirmByOwner(
                request.getPetAllowed(),
                request.getMaxWeight(),
                request.getMaxWeightInclusive(),
                requirementsOf(request),
                request.getConditionRaw()
        );

        return BusinessConverter.toClaimResult(facility);
    }

    /**
     * 이미 주인이 있는 시설이면 본인일 때만 통과시킨다. 본인이면 소유 기록은 그대로 두고 조건만
     * 갱신한다 — 뒤로 갔다 다시 제출하거나 네트워크 재시도로 같은 요청이 두 번 와도 에러를 보여줄
     * 이유가 없다. 409는 남이 등록한 경우로 한정한다.
     */
    private void requireOwnedBy(
            FacilityOwnerClaim existingClaim,
            Long facilityId,
            Long userId
    ) {
        if (!existingClaim.isOwnedBy(userId)) {
            log.info("이미 다른 사업자가 등록한 매장에 등록 시도: facilityId={}, userId={}", facilityId, userId);
            throw new GeneralException(ErrorStatus.BUSINESS4003);
        }
    }

    private void createClaim(
            Long userId,
            Facility facility,
            String maskedBusinessNumber,
            LocalDateTime verifiedAt
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));

        saveClaim(FacilityOwnerClaim.builder()
                .user(user)
                .facility(facility)
                .maskedBusinessNumber(maskedBusinessNumber)
                .verifiedAt(verifiedAt)
                .build());
    }

    // 신규 insert일 때는 FacilityOwnerClaim이 GenerationType.IDENTITY라 save() 호출 시점에 바로
    // INSERT가 나가서 여기서 제약 위반을 잡을 수 있다. 시퀀스 전략으로 바뀌면 flush가 커밋
    // 시점(이 메소드 밖)으로 밀려 이 catch가 더는 못 잡게 되니 주의.
    private void saveClaim(FacilityOwnerClaim claim) {
        try {
            facilityOwnerClaimRepository.save(claim);
        } catch (DataIntegrityViolationException exception) {
            // 위의 행 잠금이 있어 도달하기 어렵지만, 잠금을 타지 않는 경로가 생겨도 DB 제약이
            // 마지막 방어선으로 남는다. 다만 FK·not-null 위반까지 409로 뭉뚱그리지 않도록
            // 이 유니크 제약이 원인일 때만 바꾸고 나머지는 그대로 올린다.
            String constraintName = violatedConstraintName(exception);
            if (!FACILITY_UNIQUE_CONSTRAINTS.contains(constraintName)) {
                throw exception;
            }

            log.warn("매장 등록 중 유니크 제약({}) 충돌", constraintName, exception);
            throw new GeneralException(ErrorStatus.BUSINESS4003);
        }
    }

    // getMostSpecificCause()는 원인 체인의 가장 아래(SQLException)까지 내려가버려서 중간에 있는
    // Hibernate의 ConstraintViolationException을 지나쳐버린다. 제약 이름은 그 예외가 들고 있으므로
    // 체인을 직접 순회하며 처음 만나는 걸 찾는다. 제약 이름을 못 찾으면 빈 문자열을 돌려준다 —
    // Set.of()로 만든 집합은 contains(null)에 NullPointerException을 던진다.
    private String violatedConstraintName(DataIntegrityViolationException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                String constraintName = constraintViolation.getConstraintName();
                return constraintName == null ? "" : constraintName;
            }
        }
        return "";
    }

    /** 같은 조건이 여러 번 오면 체크리스트에 중복 행이 쌓이므로 걸러낸다. */
    private List<Requirement> requirementsOf(BusinessRequestDTO.ClaimRequest request) {
        return request.getRequirements() == null
                ? List.of()
                : request.getRequirements().stream().distinct().toList();
    }
}
