package com.freepets.domain.petsatisfaction.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class PetSatisfactionRequestDTO {

    private PetSatisfactionRequestDTO() {}

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpsertRequest {

        @NotNull(message = "score는 필수입니다.")
        @DecimalMin(value = "0.0", message = "score는 0.0에서 10.0 사이여야 합니다.")
        @DecimalMax(value = "10.0", message = "score는 0.0에서 10.0 사이여야 합니다.")
        private Float score;
    }
}
