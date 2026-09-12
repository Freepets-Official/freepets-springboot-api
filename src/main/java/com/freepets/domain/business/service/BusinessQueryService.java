package com.freepets.domain.business.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.freepets.domain.business.converter.BusinessConverter;
import com.freepets.domain.business.dto.BusinessRequestDTO;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.infra.nts.NtsClient;
import com.freepets.infra.nts.NtsException;
import com.freepets.infra.nts.NtsProperties;
import com.freepets.infra.nts.NtsValidationResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 사업자 인증. 국세청 진위확인 결과를 판정만 하고 아무것도 저장하지 않는다.
 *
 * <p>트랜잭션을 열지 않는다. DB를 건드리지 않는데 외부 API 응답을 트랜잭션 안에서 기다리면
 * 그동안 커넥션을 붙잡게 된다.
 */
@Slf4j
@Service
public class BusinessQueryService {

    /** 국세청 사업 상태 코드. 휴업자는 {@code 02}, 폐업자는 {@code 03}이다. */
    private static final String CONTINUING_BUSINESS = "01";

    private final NtsProperties ntsProperties;
    private final NtsClient ntsClient;

    /**
     * {@code NtsClient}를 지연 주입받는다. 이 빈은 서비스키가 없으면 생성에 실패하는데, 그냥
     * 주입받으면 그 실패가 기동 시점에 터져 키가 없는 환경에서 서버가 아예 뜨지 않는다.
     * 프록시로 받아 실제 생성을 첫 호출까지 미룬다({@code NtsConfig} 주석 참고).
     *
     * <p>설정값을 함께 받는 이유는 {@link #verify}의 주석에 적어둔다.
     */
    public BusinessQueryService(
            NtsProperties ntsProperties,
            @Lazy NtsClient ntsClient
    ) {
        this.ntsProperties = ntsProperties;
        this.ntsClient = ntsClient;
    }

    public BusinessResponseDTO.VerifyResult verify(BusinessRequestDTO.VerifyRequest request) {
        return verify(
                request.getBusinessNumber(),
                request.getRepresentativeName(),
                request.getOpeningDate()
        );
    }

    /**
     * 매장 등록도 같은 확인을 거치므로 원시값으로 받는 경로를 따로 둔다 — 등록 요청 DTO를 인증용
     * DTO로 바꿔 담지 않기 위해서다.
     */
    public BusinessResponseDTO.VerifyResult verify(
            String businessNumber,
            String representativeName,
            String openingDate
    ) {
        NtsValidationResult result = validate(businessNumber, representativeName, openingDate);

        if (!result.valid()) {
            // 어느 항목이 틀렸는지는 국세청이 알려주지 않는다. 사유 문구는 로그에만 남긴다.
            log.info("사업자 진위확인 불일치: {}", result.validMessage());
            throw new GeneralException(ErrorStatus.BUSINESS4001);
        }

        if (!CONTINUING_BUSINESS.equals(result.statusCode())) {
            // 화면이 휴업인지 폐업인지 보여줄 수 있도록 상태를 함께 내린다.
            throw new GeneralException(
                    ErrorStatus.BUSINESS4002,
                    BusinessConverter.toVerifyResult(result)
            );
        }

        return BusinessConverter.toVerifyResult(result);
    }

    private NtsValidationResult validate(
            String businessNumber,
            String representativeName,
            String openingDate
    ) {
        // 키가 없으면 클라이언트를 만들다 실패하는데, 그 예외는 스프링이 감싸서 던져 아래 catch에
        // 걸리지 않는다. 그대로 두면 서버 설정 문제가 사용자에게 500으로 나가므로 먼저 걸러낸다.
        if (!hasServiceKey()) {
            log.error("국세청 서비스키가 설정되지 않았습니다. application.yml의 nts.service-key를 확인하세요.");
            throw new GeneralException(ErrorStatus.BUSINESS5001);
        }

        try {
            return ntsClient.validate(businessNumber, representativeName, openingDate);
        } catch (NtsException exception) {
            // 사업자등록번호는 남기지 않는다.
            log.warn("국세청 진위확인 호출 실패: {}", exception.getMessage(), exception);
            throw new GeneralException(ErrorStatus.BUSINESS5001);
        }
    }

    private boolean hasServiceKey() {
        String serviceKey = ntsProperties.serviceKey();
        return serviceKey != null && !serviceKey.isBlank();
    }
}
