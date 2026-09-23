package com.freepets.domain.business.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.business.converter.BusinessConverter;
import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.Region;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.repository.RegionRepository;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

/**
 * 신규 매장 자체 등록의 DB 작업. 국세청 확인·지오코딩·중복확인은 {@link BusinessCommandService}가
 * 트랜잭션 밖에서 끝내둔다 — 외부 API 응답을 트랜잭션 안에서 기다리면 그동안 커넥션을 붙잡는다.
 *
 * <p>{@link FacilityOwnerClaimCommandService}의 자매 클래스지만, 대상 시설이 이 트랜잭션 안에서
 * 새로 생기므로 {@code findByIdForUpdate} 같은 행 잠금이 필요 없다 — 아직 존재하지 않는 행을 다른
 * 요청이 먼저 잠글 수 없기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FacilitySelfRegistrationCommandService {

    private final FacilityRepository facilityRepository;
    private final FacilityOwnerClaimRepository facilityOwnerClaimRepository;
    private final RegionRepository regionRepository;
    private final UserRepository userRepository;

    /**
     * 사업자가 고른 지역코드가 실재하는지 확인한다. 국세청 호출·지오코딩 전에 불러서, 어차피 막힐
     * 요청에 외부 호출을 쓰지 않게 한다.
     */
    @Transactional(readOnly = true)
    public Region validateRegion(
            String sidoCode,
            String sigunguCode
    ) {
        return regionRepository.findBySidoCodeAndSigunguCode(sidoCode, sigunguCode)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BUSINESS4012));
    }

    /**
     * 시설을 만들고 그 자리에서 소유권까지 확정한다. claim과 달리 심사 대기 상태를 거치지 않는다.
     */
    public BusinessResponseDTO.FacilityRegisterResult register(
            Long userId,
            BusinessRequestDTO.FacilityRegisterRequest request,
            Region region,
            BigDecimal lat,
            BigDecimal lng,
            String maskedBusinessNumber,
            LocalDateTime verifiedAt
    ) {
        User user = findUser(userId);

        Facility facility = facilityRepository.save(Facility.builder()
                .name(request.getName())
                .category(request.getCategory())
                .address(request.getAddress())
                .lat(lat)
                .lng(lng)
                .phone(request.getPhone())
                .sidoCode(region.getSidoCode())
                .sigunguCode(region.getSigunguCode())
                .sido(region.getSido())
                .sigungu(region.getSigungu())
                .petAllowed(request.getPetAllowed())
                .maxWeight(request.getMaxWeight())
                .maxWeightInclusive(request.getMaxWeightInclusive())
                .parserVersion(0)
                .source(FacilitySource.BUSINESS_SELF)
                .isActive(true)
                .petTourListed(false)
                .build());

        // builder는 체크리스트 생성과 confirmedAt 세팅을 못 한다 — 이 호출이 있어야 신뢰도가
        // CONFIRMED/OWNER로 올라간다(관리자 승인 플로우의 2단계와 동일 패턴).
        facility.confirmByOwner(
                request.getPetAllowed(),
                request.getMaxWeight(),
                request.getMaxWeightInclusive(),
                request.getRequirements(),
                request.getConditionRaw()
        );

        FacilityOwnerClaim claim = facilityOwnerClaimRepository.save(
                FacilityOwnerClaim.createApproved(user, facility, maskedBusinessNumber, verifiedAt)
        );

        return BusinessConverter.toFacilityRegisterResult(facility, claim);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
    }
}
