package com.freepets.domain.report.dto;

import com.freepets.domain.report.entity.DenialReason;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class DenialReportRequestDTO {

    private DenialReportRequestDTO() {}

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {

        @NotNull(message = "거부 이유를 선택해주세요.")
        private DenialReason reason;
    }
}
