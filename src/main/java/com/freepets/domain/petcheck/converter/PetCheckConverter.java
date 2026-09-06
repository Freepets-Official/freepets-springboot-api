package com.freepets.domain.petcheck.converter;

import java.util.List;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.petcheck.dto.PetCheckResponseDTO;
import com.freepets.domain.petcheck.entity.PetCheck;
import com.freepets.domain.petcheck.entity.PetCheckVerdict;
import com.freepets.domain.petcheck.service.PetCheckJudgeService.PetVerdict;
import com.freepets.global.util.JsonListUtil;

public class PetCheckConverter {

    private PetCheckConverter() {}

    // verifyCode는 판별 로직이 낸 값이 아니라 호출자(PetCheckCommandService)가 발급 정책에
    // 따라 결정하는 값이라 파라미터로 받는다 — 컨버터는 DTO/엔티티 모양만 맞추고, 어떤 코드를
    // 쓸지(CSPRNG 발급 여부·형식)는 여기서 정하지 않는다.
    public static PetCheckVerdict toVerdictEntity(
            PetVerdict verdict,
            String verifyCode
    ) {
        return PetCheckVerdict.builder()
                .pet(verdict.pet())
                .result(verdict.result())
                .reason(verdict.reason())
                .conditions(JsonListUtil.toJson(verdict.conditions()))
                .verifyCode(verifyCode)
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
                JsonListUtil.fromJson(verdict.getConditions()),
                verdict.getVerifyCode()
        );
    }

    // GET /verify/{code} — verdict 하나를 검증 페이지 렌더링용 데이터로 변환.
    public static PetCheckResponseDTO.VerifyPage toVerifyPage(PetCheckVerdict verdict) {
        PetCheck petCheck = verdict.getPetCheck();
        Facility facility = petCheck.getFacility();

        return new PetCheckResponseDTO.VerifyPage(
                verdict.getVerifyCode(),
                verdict.getResult(),
                facility.getName(),
                toVerifyPetInfo(verdict.getPet()),
                JsonListUtil.fromJson(verdict.getConditions()),
                verdict.getReason(),
                facility.getPetConditionRaw(),
                facility.getConfirmedAt(),
                petCheck.getCreatedAt()
        );
    }

    // pet이 null이면(반려동물 삭제됨, ON DELETE SET NULL) 화면이 대체 문구를 쓰도록 null 그대로 넘긴다.
    private static PetCheckResponseDTO.VerifyPetInfo toVerifyPetInfo(Pet pet) {
        if (pet == null) {
            return null;
        }

        return new PetCheckResponseDTO.VerifyPetInfo(
                pet.getName(),
                pet.getSpecies(),
                pet.getWeight(),
                pet.getBreedSize().getLabel(),
                pet.isVaccinated(),
                pet.getVaccinationDate()
        );
    }
}
