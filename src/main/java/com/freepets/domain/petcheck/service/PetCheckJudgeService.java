package com.freepets.domain.petcheck.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.freepets.domain.facility.entity.CheckList;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
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

    /**
     * 관광공사 반려동물 동반 조건 원문·규칙 엔진(맹견 목록, 소형견 기준 등)이 전부 개·고양이
     * 기준으로만 만들어져 있다 — 앵무새·토끼·파충류·기타 소동물에 대해서는 원문이 뭐라고
     * 적혀 있든 우리가 실제로 아는 게 없다. 이 종들은 규칙을 적용하지 않고 직접 확인하라고
     * 안내한다({@link #judgePet} 참고).
     */
    private static final Set<Kind> RULE_ENGINE_SUPPORTED_KINDS = Set.of(Kind.DOG, Kind.CAT);

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

        // 조건 원문·규칙 엔진이 개·고양이 기준으로만 만들어져 있어, 그 외 종은 이 시설 조건이
        // 실제로 어떻게 적용되는지 알 수 없다 — 룰을 적용해 잘못된 ALLOWED/DENIED를 내는 대신
        // 직접 확인하라고 안내한다.
        if (!RULE_ENGINE_SUPPORTED_KINDS.contains(pet.getKind())) {
            return conditional(
                    pet,
                    "이 시설의 반려동물 동반 조건은 개·고양이 기준으로만 확인됩니다 — %s은(는) 시설에 직접 확인해 주세요"
                            .formatted(pet.getName()),
                    List.of()
            );
        }

        BigDecimal maxWeight = facility.getMaxWeight();
        List<Requirement> requirements = requirementsOf(facility);

        // 맹견 배제 시설이 아니면서 맹견을 데려온 경우 — 동반 자체는 되지만 맹견 전용 요구조건
        // (예: 입마개 착용)이 있으면 CONDITIONAL 조건으로 안내해야 한다. 배제 시설이면 아래
        // denialReasons에서 이미 DENIED로 끊기므로 여기 조건에 넣을 필요가 없다.
        boolean appliesDangerousBreedRequiredItems = !facility.isDangerousBreedExcluded() && pet.isDangerousBreed();
        List<String> applicableConditions = applicableConditions(requirements, facility, appliesDangerousBreedRequiredItems);

        // DENIED급 사유는 하나 찾자마자 바로 끊지 않고 전부 모은다 — 체중초과만 알려주고
        // 고쳐서 다시 왔더니 예방접종 문제로 또 막히는 식의 반복 확인을 막기 위함.
        List<String> denialReasons = new ArrayList<>();

        if (facility.isDangerousBreedExcluded() && pet.isDangerousBreed()) {
            denialReasons.add("%s(%s)은(는) 법정 맹견 품종으로 이 시설의 동반이 제한됩니다"
                    .formatted(pet.getName(), pet.getSpecies()));
        }

        if (maxWeight != null && pet.getWeight().compareTo(maxWeight) > 0) {
            denialReasons.add("%s은(는) %skg으로 최대 허용 체중 %skg을 초과합니다"
                    .formatted(pet.getName(), pet.getWeight(), maxWeight));
        }

        if (requirements.contains(Requirement.VACCINATION) && !pet.isVaccinated()) {
            denialReasons.add("이 시설은 예방접종 완료를 요구하는데 접종 기록이 없습니다");
        }

        if (requirements.contains(Requirement.SMALL_ONLY) && pet.getBreedSize() != BreedSize.SMALL) {
            denialReasons.add("이 시설은 소형견만 동반 가능한데 %s은(는) 소형견이 아닙니다"
                    .formatted(pet.getName()));
        }

        if (!denialReasons.isEmpty()) {
            return denied(pet, String.join(" / ", denialReasons));
        }

        if (maxWeight != null && pet.getWeight().compareTo(maxWeight) == 0) {
            return conditional(
                    pet,
                    "체중이 허용 한도와 정확히 일치합니다 — 현장 확인을 권장합니다",
                    applicableConditions
            );
        }

        if (!applicableConditions.isEmpty()) {
            return conditional(pet, "출입은 가능하지만 아래 조건을 확인해 주세요", applicableConditions);
        }

        return allowed(pet);
    }

    /**
     * 화면에 안내할 조건 문구를 모은다. requirements(#22 규칙엔진, 전체 방문객 대상)는 항상
     * 포함하고, 맹견인데 배제 시설이 아닌 경우에만 dangerousBreedRequiredItems(#30 LLM,
     * "맹견의 경우 입마개 착용 필수" 같은 맹견 전용 조건)를 더한다 — isDangerousBreedExcluded만
     * 보고 dangerousBreedRequiredItems를 읽지 않으면, 맹견을 막지는 않지만 조건이 붙는
     * 시설(원문 685건 규모)에서 그 조건이 안내에서 통째로 빠진다.
     *
     * <p>partialAreaNote(#30 LLM, "방갈로는 반려견 동반 불가" 같은 구역 제한 메모)도 여기서
     * 같이 담는다 — 이전엔 어디서도 안 읽어서 requirements/체중경계값 안내가 나가는 동안
     * 이 정보만 항상 유실됐다.
     */
    private List<String> applicableConditions(
            List<Requirement> requirements,
            Facility facility,
            boolean appliesDangerousBreedRequiredItems
    ) {
        List<String> conditions = new ArrayList<>(conditionTexts(requirements));
        if (appliesDangerousBreedRequiredItems) {
            conditions.addAll(facility.getDangerousBreedRequiredItems());
        }
        if (facility.getPartialAreaNote() != null && !facility.getPartialAreaNote().isBlank()) {
            conditions.add(facility.getPartialAreaNote());
        }
        return conditions;
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
