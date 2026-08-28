package com.freepets.domain.petcheck.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petcheck.converter.PetCheckConverter;
import com.freepets.domain.petcheck.dto.PetCheckRequestDTO;
import com.freepets.domain.petcheck.dto.PetCheckResponseDTO;
import com.freepets.domain.petcheck.entity.PetCheck;
import com.freepets.domain.petcheck.entity.PetCheckVerdict;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.petcheck.service.PetCheckJudgeService.GroupVerdict;
import com.freepets.domain.petcheck.service.PetCheckJudgeService.PetVerdict;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PetCheckCommandService {

    private final PetCheckRepository petCheckRepository;
    private final PetRepository petRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final PetCheckJudgeService petCheckJudgeService;

    // POST /api/v1/ai/check — 개·고양이 여러 마리 그룹 판별. 규칙 엔진(Claude 호출 없음).
    // 개·고양이 외 종은 프론트가 이 API를 아예 호출하지 않는다(docs/03-ai-prompts.md §1).
    public PetCheckResponseDTO.CheckResult createCheck(
            Long userId,
            PetCheckRequestDTO.CreateRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));

        Facility facility = facilityRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4001));

        List<Pet> pets = findOwnedPets(userId, request.getPetIds());

        GroupVerdict groupVerdict = petCheckJudgeService.judgeGroup(pets, facility);

        PetCheck petCheck = PetCheck.builder()
                .user(user)
                .facility(facility)
                .overall(groupVerdict.overall())
                .build();

        for (PetVerdict verdict : groupVerdict.verdicts()) {
            PetCheckVerdict verdictEntity = PetCheckConverter.toVerdictEntity(verdict);
            petCheck.addVerdict(verdictEntity);
        }

        PetCheck savedPetCheck = petCheckRepository.save(petCheck);

        return PetCheckConverter.toCheckResult(savedPetCheck);
    }

    private List<Pet> findOwnedPets(
            Long userId,
            List<Long> petIds
    ) {
        List<Pet> pets = petRepository.findAllByPetIdInAndDeletedAtIsNull(petIds);

        if (pets.size() != petIds.size()) {
            throw new GeneralException(ErrorStatus.PET4001);
        }

        boolean ownsAll = pets.stream().allMatch(pet -> pet.isOwnedBy(userId));
        if (!ownsAll) {
            throw new GeneralException(ErrorStatus.PET4002);
        }

        return pets;
    }
}
