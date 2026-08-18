package com.freepets.domain.pet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.freepets.domain.pet.entity.BreedSize;

public class PetResponseDTO {

    private PetResponseDTO() {}

    public record PetDetail(
            Long petId,
            String name,
            String species,
            BigDecimal weight,
            BreedSize breedSize,
            String profile,
            LocalDate vaccinationDate,

            // record의 접근자도 isVaccinated()라 Jackson이 vaccinated로 줄이는 것을 막는다
            @JsonProperty("isVaccinated")
            boolean isVaccinated,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record PetList(
            List<PetDetail> pets
    ) {}

    public record CreateResult(
            Long petId
    ) {}

    public record DeleteResult(
            Long petId
    ) {}
}
