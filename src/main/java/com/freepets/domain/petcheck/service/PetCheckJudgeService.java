package com.freepets.domain.petcheck.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.freepets.domain.facility.entity.CheckList;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.petcheck.entity.PetCheckResult;

// 출입 판별 규칙 엔진. Claude 호출 없음 — docs/03-ai-prompts.md §1의 알고리즘을 그대로 옮긴 것.
// judgePet/judgeGroup 이름은 프론트 목(judge.ts의 judgeGroup)과 기준을 맞추기 위해 그대로 썼다.
//
// 시설 조건은 Facility.petAllowed/maxWeight/checkLists를 그대로 읽는다 — 이 값들은 #22에서 이미
// 들어온 PetConditionParser가 채워둔 것이라 여기서 원문을 다시 파싱하지 않는다.
// 맹견 배제(isDangerousBreedExcluded)는 FacilityConditionLlmBatchService(#30)가 채우고,
// 품종이 맹견인지는 Pet.isDangerousBreed()(동물보호법 시행규칙 기준)가 판단한다.
@Service
public class PetCheckJudgeService {

    private static final Map<Requirement, String> CONDITION_TEXT = Map.of(
            Requirement.LEASH, "리드줄 필수 착용",
            Requirement.CAGE, "이동장(켄넬) 사용 필수",
            Requirement.MUZZLE, "입마개 착용 필수",
            Requirement.VACCINATION, "예방접종 완료 필수",
            Requirement.SMALL_ONLY, "소형견만 동반 가능",
            Requirement.OUTDOOR_ONLY, "실외/야외 구역에서만 동반 가능",
            Requirement.STROLLER, "반려동물 유모차 이용 가능",
            Requirement.MANNER_BELT, "매너벨트 착용 필수"
    );

    public GroupVerdict judgeGroup(
            List<Pet> pets,
            Facility facility
    ) {
        List<PetVerdict> verdicts = pets.stream()
                .map(pet -> judgePet(pet, facility))
                .toList();

        PetCheckResult overall = verdicts.stream()
                .map(PetVerdict::result)
                .reduce(PetCheckResult.ALLOWED, PetCheckResult::mostSevere);

        return new GroupVerdict(overall, verdicts);
    }

    public PetVerdict judgePet(
            Pet pet,
            Facility facility
    ) {
        if (facility.getPetAllowed() == PetAllowed.DENIED) {
            return denied(pet, "이 시설은 반려동물 동반이 불가능합니다");
        }

        // 관광공사 반려동물 동반 목록에 없어 조건 자체가 확인 안 된 시설 — "조건 없음"과
        // "확인 안 됨"은 다른 신호라 ALLOWED로 단정하지 않고 확인이 필요하다고 알린다.
        if (facility.getPetAllowed() == PetAllowed.PENDING) {
            return conditional(
                    pet,
                    "이 시설의 반려동물 동반 정책이 아직 확인되지 않았습니다 — 방문 전 시설에 직접 확인해 주세요",
                    List.of()
            );
        }

        if (facility.isDangerousBreedExcluded() && pet.isDangerousBreed()) {
            return denied(pet, "이 시설은 맹견(동물보호법 시행규칙상 맹견 품종)의 동반을 제한합니다");
        }

        BigDecimal maxWeight = facility.getMaxWeight();
        List<Requirement> requirements = requirementsOf(facility);

        if (maxWeight != null && pet.getWeight().compareTo(maxWeight) > 0) {
            return denied(pet, "%s은(는) %skg으로 최대 허용 체중 %skg을 초과합니다"
                    .formatted(pet.getName(), pet.getWeight(), maxWeight));
        }

        if (maxWeight != null && pet.getWeight().compareTo(maxWeight) == 0) {
            return conditional(
                    pet,
                    "체중이 허용 한도와 정확히 일치합니다 — 현장 확인을 권장합니다",
                    conditionTexts(requirements)
            );
        }

        if (requirements.contains(Requirement.VACCINATION) && !pet.isVaccinated()) {
            return denied(pet, "이 시설은 예방접종 완료를 요구하는데 접종 기록이 없습니다");
        }

        if (requirements.contains(Requirement.SMALL_ONLY) && pet.getBreedSize() != BreedSize.SMALL) {
            return denied(pet, "이 시설은 소형견만 동반 가능한데 %s은(는) 소형견이 아닙니다"
                    .formatted(pet.getName()));
        }

        if (!requirements.isEmpty()) {
            return conditional(pet, "출입은 가능하지만 아래 조건을 확인해 주세요", conditionTexts(requirements));
        }

        return allowed(pet);
    }

    private List<Requirement> requirementsOf(Facility facility) {
        return facility.getCheckLists().stream()
                .map(CheckList::getType)
                .distinct()
                .toList();
    }

    private List<String> conditionTexts(List<Requirement> requirements) {
        List<String> texts = new ArrayList<>();
        for (Requirement requirement : requirements) {
            String text = CONDITION_TEXT.get(requirement);
            if (text != null) {
                texts.add(text);
            }
        }
        return texts;
    }

    private PetVerdict allowed(Pet pet) {
        return new PetVerdict(pet, PetCheckResult.ALLOWED, "모든 조건을 충족해 출입 가능합니다", List.of());
    }

    private PetVerdict conditional(
            Pet pet,
            String reason,
            List<String> conditions
    ) {
        return new PetVerdict(pet, PetCheckResult.CONDITIONAL, reason, conditions);
    }

    private PetVerdict denied(
            Pet pet,
            String reason
    ) {
        return new PetVerdict(pet, PetCheckResult.DENIED, reason, List.of());
    }

    public record PetVerdict(
            Pet pet,
            PetCheckResult result,
            String reason,
            List<String> conditions
    ) {}

    public record GroupVerdict(
            PetCheckResult overall,
            List<PetVerdict> verdicts
    ) {}
}
