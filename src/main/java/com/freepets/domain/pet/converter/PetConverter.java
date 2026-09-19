package com.freepets.domain.pet.converter;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.freepets.domain.gamification.service.LevelCurve;
import com.freepets.domain.pet.dto.PetRequestDTO;
import com.freepets.domain.pet.dto.PetResponseDTO;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.user.entity.User;
import com.freepets.global.util.BusinessZone;

public class PetConverter {

    private static final ZoneId BUSINESS_ZONE = BusinessZone.ZONE;

    private PetConverter() {}

    public static Pet toPet(
            PetRequestDTO.CreateRequest request,
            User user,
            String profileUrl
    ) {
        return Pet.builder()
                .user(user)
                .name(request.getName())
                .kind(request.getKind())
                .species(request.getSpecies())
                .weight(request.getWeight())
                .breedSize(request.getBreedSize())
                .profile(profileUrl)
                .vaccinationDate(request.getVaccinationDate())
                .nextVaccinationDate(request.getNextVaccinationDate())
                .isVaccinated(request.isVaccinated())
                .gender(request.getGender())
                .birthDate(request.getBirthDate())
                .build();
    }

    public static PetResponseDTO.PetDetail toPetDetail(Pet pet) {
        return new PetResponseDTO.PetDetail(
                pet.getPetId(),
                pet.getName(),
                pet.getKind(),
                pet.getSpecies(),
                pet.getGender(),
                pet.getBirthDate(),
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

    public static PetResponseDTO.RegistrationCard toRegistrationCard(
            Pet pet,
            List<PetResponseDTO.FavoriteFacility> favoriteFacilities
    ) {
        Long xpToNextLevel = pet.getLevel() < LevelCurve.MAX_LEVEL
                ? LevelCurve.xpToReachLevel(pet.getLevel() + 1) - pet.getTotalXp()
                : null;
        Integer age = pet.getBirthDate() != null
                ? (int) ChronoUnit.YEARS.between(pet.getBirthDate(), LocalDate.now(BUSINESS_ZONE))
                : null;

        return new PetResponseDTO.RegistrationCard(
                pet.getPetId(),
                pet.getName(),
                pet.getGender(),
                pet.getSpecies(),
                pet.getBirthDate(),
                age,
                pet.getCreatedAt(),
                pet.getLevel(),
                pet.getTotalXp(),
                xpToNextLevel,
                favoriteFacilities
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
