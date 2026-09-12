package com.freepets.domain.business.dto;

import java.math.BigDecimal;
import java.util.List;

import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class BusinessRequestDTO {

    private BusinessRequestDTO() {}

    /**
     * 사업자 인증 요청. 국세청 진위확인에 필요한 세 값을 모두 받는다.
     *
     * <p>번호만 받는 상태조회를 쓰지 않는 이유는, 사업자등록번호가 영수증에도 찍혀 있어
     * 번호만으로는 요청자가 그 사업자와 관계있는지 전혀 알 수 없기 때문이다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class VerifyRequest {

        @NotBlank(message = "사업자등록번호는 필수입니다.")
        @Pattern(regexp = "\\d{10}", message = "사업자등록번호는 하이픈 없이 10자리 숫자로 입력해주세요.")
        private String businessNumber;

        @NotBlank(message = "대표자 성명은 필수입니다.")
        private String representativeName;

        @NotBlank(message = "개업일자는 필수입니다.")
        @Pattern(regexp = "\\d{8}", message = "개업일자는 YYYYMMDD 형식으로 입력해주세요.")
        private String openingDate;
    }

    /**
     * 매장 등록 요청. 사업자 정보와 출입 조건을 함께 받는다.
     *
     * <p>사업자 정보를 다시 받는 이유는 {@code verify}가 서버에 아무 기록도 남기지 않기 때문이다.
     * 등록 요청만 직접 호출해 인증을 건너뛰는 것을 막으려면 이 시점에 한 번 더 확인해야 한다.
     * 사용자가 다시 입력하지는 않는다 — 앞 화면 입력값을 그대로 실어 보낸다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ClaimRequest {

        @NotBlank(message = "사업자등록번호는 필수입니다.")
        @Pattern(regexp = "\\d{10}", message = "사업자등록번호는 하이픈 없이 10자리 숫자로 입력해주세요.")
        private String businessNumber;

        @NotBlank(message = "대표자 성명은 필수입니다.")
        private String representativeName;

        @NotBlank(message = "개업일자는 필수입니다.")
        @Pattern(regexp = "\\d{8}", message = "개업일자는 YYYYMMDD 형식으로 입력해주세요.")
        private String openingDate;

        @NotNull(message = "반려동물 동반 가능 여부는 필수입니다.")
        private PetAllowed petAllowed;

        /** 동반 가능한 최대 체중(kg). 상한이 있을 때만 보낸다. */
        @DecimalMin(value = "0.0", inclusive = false, message = "최대 체중은 0보다 커야 합니다.")
        private BigDecimal maxWeight;

        /** {@code true}="이하", {@code false}="미만". 최대 체중이 없으면 무시된다. */
        private Boolean maxWeightInclusive;

        /** 방문객이 지켜야 할 조건. 없으면 비워 보낸다. */
        private List<Requirement> requirements;

        /** 화면에 그대로 보여줄 조건 안내문. */
        private String conditionRaw;
    }
}
