package com.freepets.domain.pet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.freepets.domain.pet.entity.BreedSize;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class PetRequestDTO {

    private PetRequestDTO() {}

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {

        @NotBlank(message = "반려동물 이름은 필수입니다.")
        @Size(max = 50, message = "반려동물 이름은 50자 이하로 입력해주세요.")
        private String name;

        @NotBlank(message = "품종은 필수입니다.")
        @Size(max = 100, message = "품종은 100자 이하로 입력해주세요.")
        private String species;

        @NotNull(message = "몸무게는 필수입니다.")
        @DecimalMin(value = "0.0", inclusive = false, message = "몸무게는 0보다 커야 합니다.")
        @Digits(integer = 3, fraction = 2, message = "몸무게는 소수점 둘째 자리까지 입력해주세요.")
        private BigDecimal weight;

        @NotNull(message = "견종 크기는 필수입니다.")
        private BreedSize breedSize;

        private String profile;

        @PastOrPresent(message = "예방접종일은 오늘 이전 날짜여야 합니다.")
        private LocalDate vaccinationDate;

        @FutureOrPresent(message = "다음 접종 예정일은 오늘 이후 날짜여야 합니다.")
        private LocalDate nextVaccinationDate;

        // Lombok이 만드는 setVaccinated 때문에 JSON 키가 vaccinated로 깎이는 것을 막는다
        @JsonProperty("isVaccinated")
        private boolean isVaccinated;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateRequest {

        @NotBlank(message = "반려동물 이름은 필수입니다.")
        @Size(max = 50, message = "반려동물 이름은 50자 이하로 입력해주세요.")
        private String name;

        @NotBlank(message = "품종은 필수입니다.")
        @Size(max = 100, message = "품종은 100자 이하로 입력해주세요.")
        private String species;

        @NotNull(message = "몸무게는 필수입니다.")
        @DecimalMin(value = "0.0", inclusive = false, message = "몸무게는 0보다 커야 합니다.")
        @Digits(integer = 3, fraction = 2, message = "몸무게는 소수점 둘째 자리까지 입력해주세요.")
        private BigDecimal weight;

        @NotNull(message = "견종 크기는 필수입니다.")
        private BreedSize breedSize;

        private String profile;

        @PastOrPresent(message = "예방접종일은 오늘 이전 날짜여야 합니다.")
        private LocalDate vaccinationDate;

        @FutureOrPresent(message = "다음 접종 예정일은 오늘 이후 날짜여야 합니다.")
        private LocalDate nextVaccinationDate;

        @JsonProperty("isVaccinated")
        private boolean isVaccinated;
    }
}
