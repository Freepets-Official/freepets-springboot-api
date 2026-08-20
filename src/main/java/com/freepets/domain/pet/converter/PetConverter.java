package com.freepets.domain.pet.converter;

import java.util.List;

import com.freepets.domain.pet.dto.PetRequestDTO;
import com.freepets.domain.pet.dto.PetResponseDTO;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.user.entity.User;

public class PetConverter {

    private PetConverter() {}

    public static Pet toPet(
            PetRequestDTO.CreateRequest request,
            User user,
            String profileUrl
    ) {
        return Pet.builder()
                .user(user)
                .name(request.getName())
                .species(request.getSpecies())
                .weight(request.getWeight())
                .breedSize(request.getBreedSize())
                .profile(profileUrl)
                .vaccinationDate(request.getVaccinationDate())
                .nextVaccinationDate(request.getNextVaccinationDate())
                .isVaccinated(request.isVaccinated())
                .build();
    }

    public static PetResponseDTO.PetDetail toPetDetail(Pet pet) {
        return new PetResponseDTO.PetDetail(
                pet.getPetId(),
                pet.getName(),
                pet.getSpecies(),
                pet.getWeight(),
                pet.getBreedSize(),
                pet.getProfile(),
                pet.getVaccinationDate(),
                pet.getNextVaccinationDate(),
                pet.isVaccinated(),
                pet.getCreatedAt(),
                pet.getUpdatedAt()
        );
    }

    public static PetResponseDTO.PetList toPetList(List<Pet> pets) {
        List<PetResponseDTO.PetDetail> petDetails = pets.stream()
                .map(PetConverter::toPetDetail)
                .toList();

        return new PetResponseDTO.PetList(petDetails);
    }

    public static PetResponseDTO.CreateResult toCreateResult(Pet pet) {
        return new PetResponseDTO.CreateResult(pet.getPetId());
    }

    public static PetResponseDTO.DeleteResult toDeleteResult(Pet pet) {
        return new PetResponseDTO.DeleteResult(pet.getPetId());
    }
}
