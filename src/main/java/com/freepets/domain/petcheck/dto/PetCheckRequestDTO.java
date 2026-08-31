package com.freepets.domain.petcheck.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class PetCheckRequestDTO {

    private PetCheckRequestDTO() {}

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {

        @NotEmpty(message = "판별할 반려동물을 1마리 이상 선택해주세요.")
        private List<Long> petIds;

        @NotNull(message = "시설을 선택해주세요.")
        private Long facilityId;
    }
}
