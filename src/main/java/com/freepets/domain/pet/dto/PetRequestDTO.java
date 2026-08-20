package com.freepets.domain.pet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

public class PetRequestDTO {

    private PetRequestDTO() {}

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {

        @NotBlank(message = "반려동물 이름은 필수입니다.")
        @Size(max = 50, message = "반려동물 이름은 50자 이하로 입력해주세요.")
        private String name;

        @NotNull(message = "동물 종류는 필수입니다.")
        private Kind kind;

        @NotBlank(message = "품종은 필수입니다.")
        @Size(max = 100, message = "품종은 100자 이하로 입력해주세요.")
        private String species;

        @NotNull(message = "몸무게는 필수입니다.")
        @DecimalMin(value = "0.0", inclusive = false, message = "몸무게는 0보다 커야 합니다.")
        @Digits(integer = 3, fraction = 2, message = "몸무게는 소수점 둘째 자리까지 입력해주세요.")
        private BigDecimal weight;

        @NotNull(message = "견종 크기는 필수입니다.")
        private BreedSize breedSize;

        private MultipartFile profile;

        @PastOrPresent(message = "예방접종일은 오늘 이전 날짜여야 합니다.")
        private LocalDate vaccinationDate;

        @FutureOrPresent(message = "다음 접종 예정일은 오늘 이후 날짜여야 합니다.")
        private LocalDate nextVaccinationDate;

        // Lombok이 만드는 setVaccinated로는 @ModelAttribute 바인딩 시 폼 필드명이 vaccinated로 깎여
        // 응답(isVaccinated)과 어긋나므로, setter를 직접 선언해 프로퍼티명을 isVaccinated로 고정한다.
        @JsonProperty("isVaccinated")
        @Setter(AccessLevel.NONE)
        private boolean isVaccinated;

        public void setIsVaccinated(boolean isVaccinated) {
            this.isVaccinated = isVaccinated;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateRequest {

        @NotBlank(message = "반려동물 이름은 필수입니다.")
        @Size(max = 50, message = "반려동물 이름은 50자 이하로 입력해주세요.")
        private String name;

        @NotNull(message = "동물 종류는 필수입니다.")
        private Kind kind;

        @NotBlank(message = "품종은 필수입니다.")
        @Size(max = 100, message = "품종은 100자 이하로 입력해주세요.")
        private String species;

        @NotNull(message = "몸무게는 필수입니다.")
        @DecimalMin(value = "0.0", inclusive = false, message = "몸무게는 0보다 커야 합니다.")
        @Digits(integer = 3, fraction = 2, message = "몸무게는 소수점 둘째 자리까지 입력해주세요.")
        private BigDecimal weight;

        @NotNull(message = "견종 크기는 필수입니다.")
        private BreedSize breedSize;

        private MultipartFile profile;

        @PastOrPresent(message = "예방접종일은 오늘 이전 날짜여야 합니다.")
        private LocalDate vaccinationDate;

        @FutureOrPresent(message = "다음 접종 예정일은 오늘 이후 날짜여야 합니다.")
        private LocalDate nextVaccinationDate;

        // CreateRequest와 같은 이유로 setter를 직접 선언해 프로퍼티명을 isVaccinated로 고정한다.
        @JsonProperty("isVaccinated")
        @Setter(AccessLevel.NONE)
        private boolean isVaccinated;

        public void setIsVaccinated(boolean isVaccinated) {
            this.isVaccinated = isVaccinated;
        }
    }
}
