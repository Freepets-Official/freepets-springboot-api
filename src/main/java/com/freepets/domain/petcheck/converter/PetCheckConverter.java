package com.freepets.domain.petcheck.converter;

import java.util.List;

import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.petcheck.dto.PetCheckResponseDTO;
import com.freepets.domain.petcheck.entity.PetCheck;
import com.freepets.domain.petcheck.entity.PetCheckVerdict;
import com.freepets.domain.petcheck.service.PetCheckJudgeService.PetVerdict;
import com.freepets.global.util.JsonListUtil;

public class PetCheckConverter {

    private PetCheckConverter() {}

    public static PetCheckVerdict toVerdictEntity(PetVerdict verdict) {
        return PetCheckVerdict.builder()
                .pet(verdict.pet())
                .result(verdict.result())
                .reason(verdict.reason())
                .conditions(JsonListUtil.toJson(verdict.conditions()))
                .build();
    }

    public static PetCheckResponseDTO.CheckResult toCheckResult(PetCheck petCheck) {
        List<PetCheckResponseDTO.VerdictDetail> verdicts = petCheck.getVerdicts().stream()
                .map(PetCheckConverter::toVerdictDetail)
                .toList();

        return new PetCheckResponseDTO.CheckResult(
                petCheck.getCheckId(),
                petCheck.getFacility().getFacilityId(),
                petCheck.getOverall(),
                verdicts
        );
    }

    public static PetCheckResponseDTO.CheckHistoryItem toHistoryItem(PetCheck petCheck) {
        List<Long> petIds = petCheck.getVerdicts().stream()
                .map(PetCheckVerdict::getPet)
                .filter(pet -> pet != null)
                .map(Pet::getPetId)
                .toList();

        return new PetCheckResponseDTO.CheckHistoryItem(
                petCheck.getCheckId(),
                petCheck.getFacility().getFacilityId(),
                petIds,
                petCheck.getOverall(),
                petCheck.getCreatedAt()
        );
    }

    private static PetCheckResponseDTO.VerdictDetail toVerdictDetail(PetCheckVerdict verdict) {
        Long petId = verdict.getPet() != null ? verdict.getPet().getPetId() : null;

        return new PetCheckResponseDTO.VerdictDetail(
                petId,
                verdict.getResult(),
                verdict.getReason(),
                JsonListUtil.fromJson(verdict.getConditions())
        );
    }
}
