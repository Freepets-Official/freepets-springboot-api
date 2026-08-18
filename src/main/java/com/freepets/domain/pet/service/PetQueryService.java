package com.freepets.domain.pet.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.pet.converter.PetConverter;
import com.freepets.domain.pet.dto.PetResponseDTO;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PetQueryService {

    private final PetRepository petRepository;

    public PetResponseDTO.PetList getMyPets(Long userId) {
        List<Pet> pets = petRepository.findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(userId);

        return PetConverter.toPetList(pets);
    }

    public PetResponseDTO.PetDetail getPet(
            Long userId,
            Long petId
    ) {
        Pet pet = findOwnedPet(userId, petId);

        return PetConverter.toPetDetail(pet);
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
