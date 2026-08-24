package com.freepets.domain.petsatisfaction.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petsatisfaction.converter.PetSatisfactionConverter;
import com.freepets.domain.petsatisfaction.dto.PetSatisfactionResponseDTO;
import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;
import com.freepets.domain.petsatisfaction.repository.PetSatisfactionRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PetSatisfactionQueryService {

    private final FacilityRepository facilityRepository;
    private final PetRepository petRepository;
    private final PetSatisfactionRepository petSatisfactionRepository;

    // 시설 하나 기준으로 내 반려동물 전체를 보여준다 — 기록이 없는 반려동물도
    // "기록 전" 상태로 함께 내려줘야 화면에서 슬라이더를 다 그릴 수 있다.
    public PetSatisfactionResponseDTO.FacilitySatisfactionList getFacilitySatisfactions(
            Long userId,
            Long facilityId
    ) {
        if (!facilityRepository.existsById(facilityId)) {
            throw new GeneralException(ErrorStatus.FACILITY4041);
        }

        List<Pet> myPets = petRepository.findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(userId);
        List<Long> petIds = myPets.stream().map(Pet::getPetId).toList();

        Map<Long, PetSatisfaction> recordedByPetId = petSatisfactionRepository
                .findAllByFacilityFacilityIdAndPetPetIdIn(facilityId, petIds)
                .stream()
                .collect(Collectors.toMap(
                        petSatisfaction -> petSatisfaction.getPet().getPetId(),
                        Function.identity()
                ));

        List<PetSatisfactionResponseDTO.FacilityItem> items = myPets.stream()
                .map(pet -> recordedByPetId.containsKey(pet.getPetId())
                        ? PetSatisfactionConverter.toFacilityItemRecorded(recordedByPetId.get(pet.getPetId()))
                        : PetSatisfactionConverter.toFacilityItemUnrecorded(pet))
                .toList();

        return new PetSatisfactionResponseDTO.FacilitySatisfactionList(items);
    }
}
