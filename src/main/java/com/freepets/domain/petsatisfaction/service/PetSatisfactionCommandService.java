package com.freepets.domain.petsatisfaction.service;

import org.hibernate.exception.ConstraintViolationException;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PetSatisfactionCommandService {

    // PetSatisfaction.java의 @UniqueConstraint 이름과 맞춰둔다.
    private static final String PET_FACILITY_UNIQUE_CONSTRAINT = "uq_pet_satisfaction_pet_facility";

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

        // 신규 insert일 때는 PetSatisfaction이 GenerationType.IDENTITY라 save() 호출 시점에
        // 바로 INSERT가 나가서 여기서 제약 위반을 잡을 수 있다. 나중에 시퀀스 전략으로 바꾸면
        // flush가 커밋 시점(이 메소드 밖)으로 밀려서 이 catch가 더는 못 잡게 되니 주의.
        PetSatisfaction saved;
        try {
            saved = petSatisfactionRepository.save(petSatisfaction);
        } catch (DataIntegrityViolationException exception) {
            // 슬라이더를 빠르게 여러 번 조작하는 등 거의 동시에 두 번 제출되면 둘 다
            // "기존 기록 없음"으로 보고 insert를 시도할 수 있다. DB의 유니크 제약
            // (pet_id, facility_id)이 뒤늦은 쪽을 막아준다.
            //
            // 다만 DataIntegrityViolationException은 FK 위반·not-null 위반 등 다른 무결성
            // 오류도 함께 잡히므로, 실제로 이 유니크 제약이 원인일 때만 409로 바꾸고
            // 그 외에는 원인을 숨기지 않고 그대로 올린다.
            if (!isUniqueConstraintViolation(exception, PET_FACILITY_UNIQUE_CONSTRAINT)) {
                throw exception;
            }

            log.warn(
                    "만족도 저장 중 유니크 제약({}) 충돌: userId={}, facilityId={}, petId={}",
                    PET_FACILITY_UNIQUE_CONSTRAINT, userId, facilityId, petId, exception
            );
            throw new GeneralException(ErrorStatus.SATISFACTION4001);
        }

        return PetSatisfactionConverter.toUpsertResult(saved);
    }

    // getMostSpecificCause()는 원인 체인의 가장 아래(SQLException)까지 내려가버려서
    // 중간에 있는 Hibernate의 ConstraintViolationException을 지나쳐버린다. 제약 이름은
    // 그 예외가 들고 있으므로, 체인을 직접 순회하며 처음 만나는 걸 찾는다.
    private boolean isUniqueConstraintViolation(
            DataIntegrityViolationException exception,
            String constraintName
    ) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return constraintName.equals(constraintViolation.getConstraintName());
            }
        }
        return false;
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
