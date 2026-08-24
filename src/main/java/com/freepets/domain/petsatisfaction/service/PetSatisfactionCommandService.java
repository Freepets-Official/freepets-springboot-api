package com.freepets.domain.petsatisfaction.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petsatisfaction.converter.PetSatisfactionConverter;
import com.freepets.domain.petsatisfaction.dto.PetSatisfactionRequestDTO;
import com.freepets.domain.petsatisfaction.dto.PetSatisfactionResponseDTO;
import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;
import com.freepets.domain.petsatisfaction.repository.PetSatisfactionRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PetSatisfactionCommandService {

    private final PetSatisfactionRepository petSatisfactionRepository;
    private final FacilityRepository facilityRepository;
    private final PetRepository petRepository;

    public PetSatisfactionResponseDTO.UpsertResult upsertSatisfaction(
            Long userId,
            Long facilityId,
            Long petId,
            PetSatisfactionRequestDTO.UpsertRequest request
    ) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4041));
        Pet pet = findOwnedPet(userId, petId);

        PetSatisfaction petSatisfaction = petSatisfactionRepository
                .findByPetPetIdAndFacilityFacilityId(petId, facilityId)
                .orElse(null);

        if (petSatisfaction == null) {
            petSatisfaction = PetSatisfactionConverter.toPetSatisfaction(pet, facility, request.getScore());
        } else {
            petSatisfaction.update(request.getScore());
        }

        PetSatisfaction saved;
        try {
            saved = petSatisfactionRepository.save(petSatisfaction);
        } catch (DataIntegrityViolationException exception) {
            // 슬라이더를 빠르게 여러 번 조작하는 등 거의 동시에 두 번 제출되면 둘 다
            // "기존 기록 없음"으로 보고 insert를 시도할 수 있다. DB의 유니크 제약
            // (pet_id, facility_id)이 뒤늦은 쪽을 막아준다.
            throw new GeneralException(ErrorStatus.SATISFACTION4001);
        }

        return PetSatisfactionConverter.toUpsertResult(saved);
    }

    // petId만 믿고 조회하면 남의 반려동물에 만족도를 기록할 수 있어(IDOR) 소유자 검증까지 한다.
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
