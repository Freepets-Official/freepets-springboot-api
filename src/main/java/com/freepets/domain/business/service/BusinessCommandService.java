package com.freepets.domain.business.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.facility.entity.Region;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.infra.geocoding.GeocodedAddress;
import com.freepets.infra.s3.S3ImageService;

import lombok.RequiredArgsConstructor;

/**
 * 매장 등록 신청. 국세청 확인, 등록증 업로드, 저장을 순서대로 엮는다.
 *
 * <p>클래스에 {@code @Transactional}을 걸지 않는다. 앞부분이 국세청 호출과 S3 업로드라는 네트워크 대기이고,
 * 그동안 DB 커넥션을 잡고 있을 이유가 없다. 저장은 {@link FacilityOwnerClaimCommandService}가
 * 자기 트랜잭션에서 처리한다({@code AuthCommandService}와 같은 구조).
 */
@Service
@RequiredArgsConstructor
public class BusinessCommandService {

    private final BusinessQueryService businessQueryService;
    private final FacilityOwnerClaimCommandService facilityOwnerClaimCommandService;
    private final FacilitySelfRegistrationCommandService facilitySelfRegistrationCommandService;
    private final FacilityDuplicateCandidateQueryService facilityDuplicateCandidateQueryService;
    private final GeocodingService geocodingService;
    private final S3ImageService s3ImageService;

    /**
     * 사업자등록정보를 다시 확인하고 등록증을 올린 뒤, 운영자 승인을 기다리는 신청을 만든다.
     *
     * <p>확인에 실패하면(불일치·휴업·폐업·국세청 통신 실패) 그대로 예외가 올라가 업로드와 저장까지 가지 않는다.
     */
    public BusinessResponseDTO.ClaimResult claim(
            Long userId,
            Long facilityId,
            BusinessRequestDTO.ClaimRequest request
    ) {
        // 어차피 막힐 신청에 국세청 호출과 파일 업로드를 쓰지 않도록 먼저 거른다. 이 확인과 저장 사이는
        // apply()가 시설 행을 잠그고 다시 막는다.
        facilityOwnerClaimCommandService.validateApplicable(userId, facilityId);

        businessQueryService.verify(
                request.getBusinessNumber(),
                request.getRepresentativeName(),
                request.getOpeningDate()
        );

        String registrationCertificateUrl = s3ImageService.uploadDocument(request.getRegistrationCertificate());

        try {
            return facilityOwnerClaimCommandService.apply(
                    userId,
                    facilityId,
                    BusinessNumberMasker.mask(request.getBusinessNumber()),
                    LocalDateTime.now(),
                    registrationCertificateUrl,
                    request
            );
        } catch (RuntimeException exception) {
            // 신청이 저장되지 않았으면 방금 올린 등록증은 아무 기록도 가리키지 않는 고아 파일이다.
            // 심사를 마친 등록증을 남겨두는 보관 정책과는 다른 경우다.
            s3ImageService.delete(registrationCertificateUrl);
            throw exception;
        }
    }

    /**
     * 신규 매장 등록. 관광공사 목록에 없는 매장을 사업자가 직접 만들면서 동시에 소유권을 갖는다. claim과
     * 달리 등록증 업로드·관리자 심사가 없어, 국세청 진위확인만 통과하면 즉시 시설이 생기고 소유권이
     * 확정된다.
     *
     * <p>호출 순서가 곧 "싼 검증부터, 비싸거나 쿼터가 유한한 외부 호출은 나중에" 원칙이다: 지역코드
     * 검증(로컬) → 지오코딩(외부) → 중복 후보 확인(로컬) → 국세청 진위확인(외부, 일일 한도 유한) → 저장.
     * 특히 중복 후보가 있는데 미확인 상태면 국세청 호출까지 가지 않아 쿼터를 아낀다.
     */
    public BusinessResponseDTO.FacilityRegisterResult registerFacility(
            Long userId,
            BusinessRequestDTO.FacilityRegisterRequest request
    ) {
        Region region = facilitySelfRegistrationCommandService.validateRegion(
                request.getSidoCode(),
                request.getSigunguCode()
        );

        GeocodedAddress geocoded = geocodingService.geocode(request.getAddress());

        List<BusinessResponseDTO.FacilityDuplicateCandidate> duplicates = facilityDuplicateCandidateQueryService.findCandidates(
                request.getName(),
                geocoded.lat().doubleValue(),
                geocoded.lng().doubleValue()
        );
        if (!duplicates.isEmpty() && !request.isDuplicateCheckAcknowledged()) {
            throw new GeneralException(
                    ErrorStatus.BUSINESS4010,
                    new BusinessResponseDTO.FacilityDuplicateCandidateList(duplicates)
            );
        }

        businessQueryService.verify(
                request.getBusinessNumber(),
                request.getRepresentativeName(),
                request.getOpeningDate()
        );

        return facilitySelfRegistrationCommandService.register(
                userId,
                request,
                region,
                geocoded.lat(),
                geocoded.lng(),
                BusinessNumberMasker.mask(request.getBusinessNumber()),
                LocalDateTime.now()
        );
    }
}
