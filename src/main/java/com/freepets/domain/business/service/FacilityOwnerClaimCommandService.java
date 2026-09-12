package com.freepets.domain.business.service;

import java.time.LocalDateTime;
import java.util.List;

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
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FacilityOwnerClaimCommandService {

    private static final String FACILITY_UNIQUE_CONSTRAINT = "uk_facility_owner_claims_facility_id";

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

        facilityOwnerClaimRepository.findByFacility_FacilityId(facilityId)
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
            if (!isUniqueConstraintViolation(exception, FACILITY_UNIQUE_CONSTRAINT)) {
                throw exception;
            }

            log.warn("매장 등록 중 유니크 제약({}) 충돌", FACILITY_UNIQUE_CONSTRAINT, exception);
            throw new GeneralException(ErrorStatus.BUSINESS4003);
        }
    }

    // getMostSpecificCause()는 원인 체인의 가장 아래(SQLException)까지 내려가버려서 중간에 있는
    // Hibernate의 ConstraintViolationException을 지나쳐버린다. 제약 이름은 그 예외가 들고 있으므로
    // 체인을 직접 순회하며 처음 만나는 걸 찾는다.
    private boolean isUniqueConstraintViolation(
            DataIntegrityViolationException exception,
            String constraintName
    ) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return constraintName.equals(constraintViolation.getConstraintName());
            }
        }
        return false;
    }

    /** 같은 조건이 여러 번 오면 체크리스트에 중복 행이 쌓이므로 걸러낸다. */
    private List<Requirement> requirementsOf(BusinessRequestDTO.ClaimRequest request) {
        return request.getRequirements() == null
                ? List.of()
                : request.getRequirements().stream().distinct().toList();
    }
}
