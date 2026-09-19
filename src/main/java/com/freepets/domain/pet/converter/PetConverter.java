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

public class PetConverter {

    // 서버 프로세스는 항상 UTC로 고정돼 있다(FreepetsServerApplication의 static 블록) — "오늘"을
    // JVM 기본 타임존(UTC)으로 계산하면, 한국 시간으로는 이미 생일이 지난 자정~오전 9시 사이에
    // 나이가 하루(엄밀히는 그 시간대만큼) 늦게 올라간다. 실제 사용자가 있는 KST 기준으로 계산한다.
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

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
