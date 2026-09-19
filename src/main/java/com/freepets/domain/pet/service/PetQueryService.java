package com.freepets.domain.pet.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.pet.converter.PetConverter;
import com.freepets.domain.pet.dto.PetResponseDTO;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petsatisfaction.service.PetSatisfactionQueryService;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PetQueryService {

    private final PetRepository petRepository;
    private final PetSatisfactionQueryService petSatisfactionQueryService;

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

    // GET /api/v1/pets/{petId}/card — "반려동물 등록증" 카드. getPet(수정 화면용)과 달리
    // 만족도 도메인(최애 장소 TOP3)까지 합쳐서 내려준다. petsatisfaction의 응답 DTO를 pet의
    // 응답 계약에 그대로 끼워넣지 않도록, 도메인 경계에서 pet 자신의 타입으로 옮겨 담는다.
    public PetResponseDTO.RegistrationCard getPetCard(
            Long userId,
            Long petId
    ) {
        Pet pet = findOwnedPet(userId, petId);
        List<PetResponseDTO.FavoriteFacility> favoriteFacilities = petSatisfactionQueryService.getTopFacilitiesForPet(petId).stream()
                .map(topFacility -> new PetResponseDTO.FavoriteFacility(
                        topFacility.facilityId(),
                        topFacility.facilityName(),
                        topFacility.category(),
                        topFacility.score()
                ))
                .toList();

        return PetConverter.toRegistrationCard(pet, favoriteFacilities);
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
