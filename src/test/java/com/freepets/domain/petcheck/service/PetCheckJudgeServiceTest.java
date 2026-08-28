package com.freepets.domain.petcheck.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.petcheck.entity.PetCheckResult;
import com.freepets.domain.petcheck.service.PetCheckJudgeService.GroupVerdict;
import com.freepets.domain.petcheck.service.PetCheckJudgeService.PetVerdict;

// docs/03-ai-prompts.md §1의 판별 알고리즘을 그대로 검증. Facility.petAllowed/maxWeight/checkLists는
// #22의 PetConditionParser가 채워둔다고 가정하고, 여기선 그 결과값만 갖고 판별 로직을 검증한다.
class PetCheckJudgeServiceTest {

    private final PetCheckJudgeService judgeService = new PetCheckJudgeService();

    @Test
    void 조건_없는_시설은_ALLOWED() {
        Facility facility = facility(PetAllowed.ALLOWED, null, List.of());
        Pet pet = pet("몽이", "말티즈", "3.2", true);

        PetVerdict verdict = judgeService.judgePet(pet, facility);

        assertEquals(PetCheckResult.ALLOWED, verdict.result());
    }

    @Test
    void 시설이_반려동물_동반_자체가_불가능하면_DENIED() {
        Facility facility = facility(PetAllowed.DENIED, null, List.of());
        Pet pet = pet("몽이", "말티즈", "3.2", true);

        PetVerdict verdict = judgeService.judgePet(pet, facility);

        assertEquals(PetCheckResult.DENIED, verdict.result());
    }

    @Test
    void 시설의_동반_정책이_확인_안_됐으면_CONDITIONAL() {
        Facility facility = facility(PetAllowed.PENDING, null, List.of());
        Pet pet = pet("몽이", "말티즈", "3.2", true);

        PetVerdict verdict = judgeService.judgePet(pet, facility);

        assertEquals(PetCheckResult.CONDITIONAL, verdict.result());
        assertTrue(verdict.reason().contains("확인"));
    }

    @Test
    void 체중이_최대_허용치를_초과하면_DENIED() {
        Facility facility = facility(PetAllowed.ALLOWED, new BigDecimal("10.0"), List.of());
        Pet pet = pet("보리", "골든리트리버", "27.5", true);

        PetVerdict verdict = judgeService.judgePet(pet, facility);

        assertEquals(PetCheckResult.DENIED, verdict.result());
        assertTrue(verdict.reason().contains("초과"));
    }

    @Test
    void 체중이_경계값과_정확히_같으면_CONDITIONAL() {
        Facility facility = facility(PetAllowed.ALLOWED, new BigDecimal("10.0"), List.of());
        Pet pet = pet("몽이", "말티즈", "10.0", true);

        PetVerdict verdict = judgeService.judgePet(pet, facility);

        assertEquals(PetCheckResult.CONDITIONAL, verdict.result());
    }

    @Test
    void 예방접종_필요한데_미접종이면_DENIED() {
        Facility facility = facility(PetAllowed.ALLOWED, null, List.of(Requirement.VACCINATION));
        Pet pet = pet("몽이", "말티즈", "3.2", false);

        PetVerdict verdict = judgeService.judgePet(pet, facility);

        assertEquals(PetCheckResult.DENIED, verdict.result());
    }

    @Test
    void 실외구역만_가능하면_CONDITIONAL이고_조건문구가_담긴다() {
        Facility facility = facility(PetAllowed.ALLOWED, null, List.of(Requirement.OUTDOOR_ONLY, Requirement.LEASH));
        Pet pet = pet("몽이", "말티즈", "3.2", true);

        PetVerdict verdict = judgeService.judgePet(pet, facility);

        assertEquals(PetCheckResult.CONDITIONAL, verdict.result());
        assertTrue(verdict.conditions().contains("실외/야외 구역에서만 동반 가능"));
        assertTrue(verdict.conditions().contains("리드줄 필수 착용"));
    }

    @Test
    void 소형견_전용_시설에_대형견이면_DENIED() {
        Facility facility = facility(PetAllowed.ALLOWED, null, List.of(Requirement.SMALL_ONLY));
        Pet pet = pet("두목", "골든리트리버", "30.0", true, BreedSize.LARGE);

        PetVerdict verdict = judgeService.judgePet(pet, facility);

        assertEquals(PetCheckResult.DENIED, verdict.result());
    }

    @Test
    void 소형견_전용_시설에_소형견이면_CONDITIONAL() {
        Facility facility = facility(PetAllowed.ALLOWED, null, List.of(Requirement.SMALL_ONLY));
        Pet pet = pet("몽이", "말티즈", "3.2", true, BreedSize.SMALL);

        PetVerdict verdict = judgeService.judgePet(pet, facility);

        assertEquals(PetCheckResult.CONDITIONAL, verdict.result());
    }

    @Test
    void 맹견_배제_시설에_맹견이면_DENIED() {
        Facility facility = facility(PetAllowed.ALLOWED, null, List.of());
        ReflectionTestUtils.setField(facility, "isDangerousBreedExcluded", true);
        Pet pet = pet("두목", "로트와일러", "40.0", true, BreedSize.LARGE);

        PetVerdict verdict = judgeService.judgePet(pet, facility);

        assertEquals(PetCheckResult.DENIED, verdict.result());
    }

    @Test
    void 맹견_배제_시설이어도_맹견이_아니면_DENIED_아님() {
        Facility facility = facility(PetAllowed.ALLOWED, null, List.of());
        ReflectionTestUtils.setField(facility, "isDangerousBreedExcluded", true);
        Pet pet = pet("몽이", "말티즈", "3.2", true);

        PetVerdict verdict = judgeService.judgePet(pet, facility);

        assertEquals(PetCheckResult.ALLOWED, verdict.result());
    }

    @Test
    void 맹견_배제가_아닌_시설이면_맹견이어도_DENIED_아님() {
        Facility facility = facility(PetAllowed.ALLOWED, null, List.of());
        Pet pet = pet("두목", "로트와일러", "40.0", true, BreedSize.LARGE);

        PetVerdict verdict = judgeService.judgePet(pet, facility);

        assertEquals(PetCheckResult.ALLOWED, verdict.result());
    }

    @Test
    void 그룹_판별은_하나라도_DENIED면_overall이_DENIED() {
        Facility facility = facility(PetAllowed.ALLOWED, new BigDecimal("10.0"), List.of(Requirement.LEASH));
        Pet 몽이 = pet("몽이", "말티즈", "3.2", true);
        Pet 보리 = pet("보리", "골든리트리버", "27.5", true);

        GroupVerdict group = judgeService.judgeGroup(List.of(몽이, 보리), facility);

        assertEquals(PetCheckResult.DENIED, group.overall());
        assertEquals(2, group.verdicts().size());
    }

    private Facility facility(
            PetAllowed petAllowed,
            BigDecimal maxWeight,
            List<Requirement> requirements
    ) {
        Facility facility = Facility.builder()
                .name("테스트 시설")
                .category(FacilityCategory.CAFE)
                .address("강원 강릉시")
                .lat(new BigDecimal("37.751853"))
                .lng(new BigDecimal("128.876057"))
                .petAllowed(petAllowed)
                .maxWeight(maxWeight)
                .build();
        facility.replaceRequirements(requirements);
        return facility;
    }

    private Pet pet(
            String name,
            String species,
            String weight,
            boolean vaccinated
    ) {
        return pet(name, species, weight, vaccinated, BreedSize.SMALL);
    }

    private Pet pet(
            String name,
            String species,
            String weight,
            boolean vaccinated,
            BreedSize breedSize
    ) {
        return Pet.builder()
                .name(name)
                .kind(Kind.DOG)
                .species(species)
                .weight(new BigDecimal(weight))
                .breedSize(breedSize)
                .vaccinationDate(vaccinated ? LocalDate.now().minusMonths(3) : null)
                .isVaccinated(vaccinated)
                .build();
    }
}
