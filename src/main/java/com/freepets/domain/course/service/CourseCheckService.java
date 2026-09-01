package com.freepets.domain.course.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.course.dto.CourseCheckResponseDTO;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petcheck.converter.PetCheckConverter;
import com.freepets.domain.petcheck.entity.PetCheck;
import com.freepets.domain.petcheck.entity.PetCheckResult;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.petcheck.service.PetCheckJudgeService;
import com.freepets.domain.petcheck.service.PetCheckJudgeService.GroupVerdict;
import com.freepets.domain.petcheck.service.PetCheckJudgeService.PetVerdict;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.global.util.GeoUtils;

import lombok.RequiredArgsConstructor;

/**
 * POST /api/v1/ai/course-check — 사용자가 직접 담은 코스(시설 여러 개)를 06번 판별과 동일한
 * 규칙({@link PetCheckJudgeService#judgeGroup})으로 일괄 검증한다. 스톱마다 판별 결과를
 * {@code pet_checks} 이력에 남긴다(08-course-check.md "확인 필요" 1번, 이력으로 남기는 쪽으로 결정).
 *
 * <p>{@code CONDITIONAL}은 대안을 제안하지 않고 통과 처리한다 — 반려동물이 못 들어가는 게 아니라
 * 조건부로 들어갈 수 있는 것이므로. {@code DENIED}인 스톱만 대안을 찾는다.
 *
 * <p>대안은 미리 저장해둔 테이블을 찾는 게 아니라 그 자리에서 계산한다(08-course-check.md
 * "같은 카테고리·그룹 통과·거리순") — 같은 {@code category} · 이 코스에 이미 없음 · 선택한
 * 반려동물 전체가 {@code judgeGroup}을 통과(DENIED 아님)하는 시설 중 가장 가까운 1곳. 조건에
 * 맞는 곳이 하나도 없으면 {@code null} — 프론트가 "같은 성격의 대체 시설을 찾지 못했어요. 이
 * 스톱은 빼는 것을 권장해요"로 안내한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CourseCheckService {

    /** 첫 스톱 시각. 08-course-check.md 데모 고정값. */
    private static final LocalTime FIRST_STOP_TIME = LocalTime.of(10, 0);

    /** 스톱 간 간격(분). 08-course-check.md 데모 고정값. */
    private static final int MINUTES_PER_STOP = 90;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final FacilityRepository facilityRepository;
    private final PetCheckRepository petCheckRepository;
    private final PetCheckJudgeService petCheckJudgeService;

    public CourseCheckResponseDTO.CourseCheckResult checkCourse(
            Long userId,
            List<Long> petIds,
            List<Long> facilityIds
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        List<Pet> pets = findOwnedPets(userId, petIds);
        List<Facility> facilitiesInOrder = findFacilitiesInOrder(facilityIds);
        Set<Long> courseFacilityIds = Set.copyOf(facilityIds);

        List<CourseCheckResponseDTO.Stop> stops = new ArrayList<>();
        PetCheckResult courseOverall = PetCheckResult.ALLOWED;
        long blockedCount = 0;

        for (int i = 0; i < facilitiesInOrder.size(); i++) {
            Facility facility = facilitiesInOrder.get(i);
            GroupVerdict verdict = petCheckJudgeService.judgeGroup(pets, facility);

            saveAsHistory(user, facility, verdict);

            boolean isDenied = verdict.overall() == PetCheckResult.DENIED;
            stops.add(new CourseCheckResponseDTO.Stop(
                    toFacilitySummary(facility),
                    stopTimeOf(i),
                    toStopVerdicts(verdict),
                    verdict.overall(),
                    isDenied ? findAlternative(facility, courseFacilityIds, pets) : null
            ));

            if (isDenied) {
                blockedCount++;
            }
            courseOverall = PetCheckResult.mostSevere(courseOverall, verdict.overall());
        }

        return new CourseCheckResponseDTO.CourseCheckResult(courseOverall, blockedCount, stops);
    }

    private CourseCheckResponseDTO.Alternative findAlternative(
            Facility blockedFacility,
            Set<Long> courseFacilityIds,
            List<Pet> pets
    ) {
        List<Facility> candidates = facilityRepository
                .findAllByCategoryAndIsActiveTrueAndPetAllowedNotAndFacilityIdNotIn(
                        blockedFacility.getCategory(), PetAllowed.DENIED, courseFacilityIds
                );

        return candidates.stream()
                .filter(candidate -> candidate.getLat() != null && candidate.getLng() != null)
                .filter(candidate -> petCheckJudgeService.judgeGroup(pets, candidate).overall() != PetCheckResult.DENIED)
                .map(candidate -> Map.entry(candidate, distanceFrom(blockedFacility, candidate)))
                .min(Comparator.comparingDouble(Map.Entry::getValue))
                .map(nearest -> toAlternative(nearest.getKey(), nearest.getValue()))
                .orElse(null);
    }

    private double distanceFrom(
            Facility origin,
            Facility target
    ) {
        return GeoUtils.distanceMeters(origin.getLat(), origin.getLng(), target.getLat(), target.getLng());
    }

    private CourseCheckResponseDTO.Alternative toAlternative(
            Facility alternative,
            double distanceMeters
    ) {
        return new CourseCheckResponseDTO.Alternative(
                alternative.getFacilityId(),
                alternative.getName(),
                Math.round(distanceMeters / 100.0) / 10.0 // km, 소수 첫째 자리
        );
    }

    private void saveAsHistory(
            User user,
            Facility facility,
            GroupVerdict verdict
    ) {
        PetCheck petCheck = PetCheck.builder()
                .user(user)
                .facility(facility)
                .overall(verdict.overall())
                .build();

        for (PetVerdict petVerdict : verdict.verdicts()) {
            petCheck.addVerdict(PetCheckConverter.toVerdictEntity(petVerdict));
        }

        petCheckRepository.save(petCheck);
    }

    private List<CourseCheckResponseDTO.StopVerdict> toStopVerdicts(GroupVerdict verdict) {
        return verdict.verdicts().stream()
                .map(petVerdict -> new CourseCheckResponseDTO.StopVerdict(
                        petVerdict.pet().getPetId(),
                        petVerdict.pet().getName(),
                        petVerdict.result(),
                        petVerdict.reason(),
                        petVerdict.conditions()
                ))
                .toList();
    }

    private CourseCheckResponseDTO.FacilitySummary toFacilitySummary(Facility facility) {
        return new CourseCheckResponseDTO.FacilitySummary(
                facility.getFacilityId(),
                facility.getName(),
                facility.getCategory()
        );
    }

    private String stopTimeOf(int stopIndex) {
        return FIRST_STOP_TIME.plusMinutes((long) MINUTES_PER_STOP * stopIndex).format(TIME_FORMAT);
    }

    private List<Facility> findFacilitiesInOrder(List<Long> facilityIds) {
        Map<Long, Facility> byId = new LinkedHashMap<>();
        facilityRepository.findAllById(facilityIds).forEach(facility -> byId.put(facility.getFacilityId(), facility));

        List<Facility> ordered = new ArrayList<>();
        for (Long facilityId : facilityIds) {
            Facility facility = byId.get(facilityId);
            if (facility == null) {
                throw new GeneralException(ErrorStatus.FACILITY4001);
            }
            ordered.add(facility);
        }
        return ordered;
    }

    // PetCheckCommandService.findOwnedPets와 같은 이유 — petIds 중복 제거 후 소유 검증.
    private List<Pet> findOwnedPets(
            Long userId,
            List<Long> petIds
    ) {
        List<Long> distinctPetIds = petIds.stream().distinct().toList();
        List<Pet> pets = petRepository.findAllByPetIdInAndDeletedAtIsNull(distinctPetIds);

        if (pets.size() != distinctPetIds.size()) {
            throw new GeneralException(ErrorStatus.PET4001);
        }

        boolean isAllOwnedByUser = pets.stream().allMatch(pet -> pet.isOwnedBy(userId));
        if (!isAllOwnedByUser) {
            throw new GeneralException(ErrorStatus.PET4002);
        }

        return pets;
    }

}
