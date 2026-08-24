package com.freepets.domain.petsatisfaction.converter;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.petsatisfaction.dto.PetSatisfactionResponseDTO;
import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;

public class PetSatisfactionConverter {

    private PetSatisfactionConverter() {}

    public static PetSatisfaction toPetSatisfaction(
            Pet pet,
            Facility facility,
            float score
    ) {
        return PetSatisfaction.builder()
                .pet(pet)
                .facility(facility)
                .score(score)
                .build();
    }

    public static PetSatisfactionResponseDTO.UpsertResult toUpsertResult(PetSatisfaction petSatisfaction) {
        return new PetSatisfactionResponseDTO.UpsertResult(
                petSatisfaction.getPet().getPetId(),
                petSatisfaction.getFacility().getFacilityId(),
                petSatisfaction.getScore()
        );
    }

    public static PetSatisfactionResponseDTO.FacilityItem toFacilityItemRecorded(PetSatisfaction petSatisfaction) {
        return new PetSatisfactionResponseDTO.FacilityItem(
                petSatisfaction.getPet().getPetId(),
                petSatisfaction.getPet().getName(),
                petSatisfaction.getScore(),
                true
        );
    }

    public static PetSatisfactionResponseDTO.FacilityItem toFacilityItemUnrecorded(Pet pet) {
        return new PetSatisfactionResponseDTO.FacilityItem(
                pet.getPetId(),
                pet.getName(),
                null,
                false
        );
    }
}
