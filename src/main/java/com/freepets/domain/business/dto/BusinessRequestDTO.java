package com.freepets.domain.business.dto;

import jakarta.validation.constraints.NotBlank;
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
}
