package com.freepets.domain.petsatisfaction.service;

import java.util.Comparator;
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

    private static final int TOP_FACILITIES_LIMIT = 3;

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

    // 홈 "아이별 좋아한 곳 TOP" 카드용 — 반려동물별로 묶어서 점수 높은 순 상위 3개 시설만 미리
    // 잘라 내려준다. 카드가 여러 마리를 스택으로 한 번에 보여줘서, 반려동물마다 따로 요청하지
    // 않도록 한 번에 전부 계산한다. 기록이 하나도 없는 반려동물은 결과에서 빠진다.
    public PetSatisfactionResponseDTO.MySatisfactionList getMySatisfactions(Long userId) {
        List<PetSatisfaction> satisfactions = petSatisfactionRepository
                .findAllByPetUserIdAndPetDeletedAtIsNull(userId);

        Map<Long, List<PetSatisfaction>> byPetId = satisfactions.stream()
                .collect(Collectors.groupingBy(petSatisfaction -> petSatisfaction.getPet().getPetId()));

        List<PetSatisfactionResponseDTO.PetTopFacilities> pets = byPetId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<PetSatisfaction> top = entry.getValue().stream()
                            .sorted(Comparator.comparingDouble(PetSatisfaction::getScore).reversed()
                                    .thenComparing(petSatisfaction -> petSatisfaction.getFacility().getFacilityId()))
                            .limit(TOP_FACILITIES_LIMIT)
                            .toList();
                    String petName = top.get(0).getPet().getName();

                    return PetSatisfactionConverter.toPetTopFacilities(entry.getKey(), petName, top);
                })
                .toList();

        return new PetSatisfactionResponseDTO.MySatisfactionList(pets);
    }
}
