package com.freepets.domain.pet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.pet.converter.PetConverter;
import com.freepets.domain.pet.dto.PetRequestDTO;
import com.freepets.domain.pet.dto.PetResponseDTO;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PetCommandService {

    private final PetRepository petRepository;
    private final UserRepository userRepository;

    public PetResponseDTO.CreateResult createPet(
            Long userId,
            PetRequestDTO.CreateRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));

        Pet pet = PetConverter.toPet(request, user);
        Pet savedPet = petRepository.save(pet);

        return PetConverter.toCreateResult(savedPet);
    }

    public PetResponseDTO.PetDetail updatePet(
            Long userId,
            Long petId,
            PetRequestDTO.UpdateRequest request
    ) {
        Pet pet = findOwnedPet(userId, petId);

        pet.update(
                request.getName(),
                request.getSpecies(),
                request.getWeight(),
                request.getBreedSize(),
                request.getProfile(),
                request.getVaccinationDate(),
                request.isVaccinated()
        );

        return PetConverter.toPetDetail(pet);
    }

    public PetResponseDTO.DeleteResult deletePet(
            Long userId,
            Long petId
    ) {
        Pet pet = findOwnedPet(userId, petId);
        pet.delete();

        return PetConverter.toDeleteResult(pet);
    }

    private Pet findOwnedPet(
            Long userId,
            Long petId
    ) {
        Pet pet = petRepository.findByPetIdAndDeletedAtIsNull(petId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.PET4001));

        if (!pet.isOwnedBy(userId)) {
            throw new GeneralException(ErrorStatus.PET4002);
        }

        return pet;
    }
}
