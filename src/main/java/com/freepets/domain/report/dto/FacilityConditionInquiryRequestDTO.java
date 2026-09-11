package com.freepets.domain.report.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class FacilityConditionInquiryRequestDTO {

    private FacilityConditionInquiryRequestDTO() {}

    @Getter
    @Setter
    @NoArgsConstructor
    public static class InquireRequest {

        // 완전히 선택 — 본문 자체를 안 보내도 요청은 유효하다(컨트롤러에서 @RequestBody(required
        // = false)로 받는다).
        @Size(max = 500, message = "메모는 500자 이하로 입력해주세요.")
        private String memo;
    }
}
