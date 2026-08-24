package com.freepets.domain.petsatisfaction.dto;

public class PetSatisfactionResponseDTO {

    private PetSatisfactionResponseDTO() {}

    public record UpsertResult(
            Long petId,
            Long facilityId,
            float score
    ) {}
}
