package com.freepets.domain.business.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;

import lombok.RequiredArgsConstructor;

/**
 * 매장 등록. 국세청 확인과 저장을 순서대로 엮는다.
 *
 * <p>클래스에 {@code @Transactional}을 걸지 않는다. 앞부분이 국세청을 부르는 네트워크 대기이고,
 * 그동안 DB 커넥션을 잡고 있을 이유가 없다. 저장은 {@link FacilityOwnerClaimCommandService}가
 * 자기 트랜잭션에서 처리한다({@code AuthCommandService}와 같은 구조).
 */
@Service
@RequiredArgsConstructor
public class BusinessCommandService {

    private final BusinessQueryService businessQueryService;
    private final FacilityOwnerClaimCommandService facilityOwnerClaimCommandService;

    /**
     * 사업자등록정보를 다시 확인한 뒤 소유 기록을 만들고 출입 조건을 확정한다.
     *
     * <p>확인에 실패하면(불일치·휴업·폐업·국세청 통신 실패) 그대로 예외가 올라가 저장까지 가지 않는다.
     */
    public BusinessResponseDTO.ClaimResult claim(
            Long userId,
            Long facilityId,
            BusinessRequestDTO.ClaimRequest request
    ) {
        businessQueryService.verify(
                request.getBusinessNumber(),
                request.getRepresentativeName(),
                request.getOpeningDate()
        );

        return facilityOwnerClaimCommandService.claim(
                userId,
                facilityId,
                BusinessNumberMasker.mask(request.getBusinessNumber()),
                LocalDateTime.now(),
                request
        );
    }
}
