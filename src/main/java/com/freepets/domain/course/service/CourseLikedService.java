package com.freepets.domain.course.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.CourseDistanceOption;
import com.freepets.domain.course.entity.CourseTheme;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;
import com.freepets.domain.petsatisfaction.repository.FacilityAverageSatisfaction;
import com.freepets.domain.petsatisfaction.repository.PetSatisfactionRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

// GET /api/v1/courses/liked — "우리 아이 취향 코스". 07-courses.md 알고리즘 참고.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseLikedService {

    /** 이 값 이상이어야 "취향에 맞는 곳"으로 인정한다. 07-courses.md LIKED_THRESHOLD. */
    private static final double LIKED_THRESHOLD = 6.5;

    /** 이 미만이면 코스로 묶을 의미가 없다고 보고 COURSE4002. */
    private static final int MINIMUM_CANDIDATE_COUNT = 2;

    private final PetRepository petRepository;
    private final PetSatisfactionRepository petSatisfactionRepository;
    private final FacilityRepository facilityRepository;
    private final CourseAssemblyService courseAssemblyService;

    public CourseResponseDTO.LikedCourseResult getLikedCourse(
            Long userId,
            List<Long> petIds,
            CourseDistanceOption maxDistanceM,
            String sido,
            String sigungu,
            Set<CourseTheme> themes
    ) {
        double maxDistanceMeters = (maxDistanceM != null ? maxDistanceM : CourseDistanceOption.FIVE_KM).getMeters();
        List<Pet> pets = findOwnedPets(userId, petIds);

        // 1) 후보 풀 — petIds 중 최소 한 마리가 방문 기록을 남긴 시설.
        List<PetSatisfaction> satisfactionsOfSelectedPets = petSatisfactionRepository.findAllByPetPetIdIn(petIds);
        Map<Long, List<PetSatisfaction>> byFacilityId = satisfactionsOfSelectedPets.stream()
                .collect(Collectors.groupingBy(satisfaction -> satisfaction.getFacility().getFacilityId()));

        if (byFacilityId.isEmpty()) {
            throw new GeneralException(ErrorStatus.COURSE4002);
        }

        // 2) 6.5 이상 필터 — petIds로 안 좁힌 전체 반려동물 평균(avgSatisfaction과 동일 값)을 쓴다.
        Map<Long, Double> averageScoreByFacilityId = petSatisfactionRepository
                .findAverageScoreByFacilityIdIn(byFacilityId.keySet()).stream()
                .collect(Collectors.toMap(
                        FacilityAverageSatisfaction::getFacilityId,
                        FacilityAverageSatisfaction::getAvgScore
                ));

        List<Long> finalCandidateFacilityIds = averageScoreByFacilityId.entrySet().stream()
                .filter(entry -> entry.getValue() >= LIKED_THRESHOLD)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();

        if (finalCandidateFacilityIds.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new GeneralException(ErrorStatus.COURSE4002);
        }

        // 3) avgSatisfaction desc 순서를 보존한 채로 Facility 엔티티 로드.
        List<Facility> candidatesScoreDescSorted = loadInOrder(finalCandidateFacilityIds);

        // 3-1) 지역·테마 필터(둘 다 선택 사항) — 지정 안 하면 기존과 동일하게 전부 대상. 테마를
        // 여러 개 고르면 OR 조건(하나라도 속하면 통과)이다.
        candidatesScoreDescSorted = candidatesScoreDescSorted.stream()
                .filter(facility -> matchesRegionAndTheme(facility, sido, sigungu, themes))
                .toList();
        if (candidatesScoreDescSorted.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new GeneralException(ErrorStatus.COURSE4002);
        }

        // 4) 카테고리 다양성 + 거리 제약 + 동선 조립. 거리 제약 때문에 후보는 충분해도 실제
        // 채택된 스톱은 더 줄 수 있어 여기서 한 번 더 확인한다(2번의 사전 확인만으론 부족).
        List<Facility> stops = courseAssemblyService.assemble(candidatesScoreDescSorted, maxDistanceMeters);
        if (stops.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new GeneralException(ErrorStatus.COURSE4002);
        }

        String title = pets.stream()
                .map(Pet::getName)
                .collect(Collectors.joining("·")) + "가 좋아한 곳";

        List<CourseResponseDTO.LikedStop> stopDtos = stops.stream()
                .map(facility -> toLikedStop(facility, averageScoreByFacilityId, byFacilityId, petIds))
                .toList();

        return new CourseResponseDTO.LikedCourseResult(title, stopDtos);
    }

    private CourseResponseDTO.LikedStop toLikedStop(
            Facility facility,
            Map<Long, Double> averageScoreByFacilityId,
            Map<Long, List<PetSatisfaction>> byFacilityId,
            List<Long> petIds
    ) {
        double avgSatisfaction = Math.round(averageScoreByFacilityId.get(facility.getFacilityId()) * 10) / 10.0;

        List<CourseResponseDTO.ReasonPet> reasonPets = byFacilityId
                .getOrDefault(facility.getFacilityId(), List.of()).stream()
                .filter(satisfaction -> petIds.contains(satisfaction.getPet().getPetId()))
                .map(satisfaction -> new CourseResponseDTO.ReasonPet(
                        satisfaction.getPet().getPetId(),
                        satisfaction.getPet().getName(),
                        satisfaction.getScore()
                ))
                .toList();

        return new CourseResponseDTO.LikedStop(
                facility.getFacilityId(),
                facility.getName(),
                facility.getCategory(),
                avgSatisfaction,
                reasonPets
        );
    }

    // CourseSimilarService.matchesRegionAndTheme와 같은 이유 — sido/sigungu/themes 셋 다 선택
    // 사항이라 넘어오지 않은 조건은 통과시킨다. themes는 여러 개면 OR — 그중 하나라도 카테고리가
    // 맞으면 통과.
    private boolean matchesRegionAndTheme(
            Facility facility,
            String sido,
            String sigungu,
            Set<CourseTheme> themes
    ) {
        if (sido != null && !sido.equals(facility.getSido())) {
            return false;
        }
        if (sigungu != null && !sigungu.equals(facility.getSigungu())) {
            return false;
        }
        return themes == null || themes.isEmpty()
                || themes.stream().anyMatch(theme -> theme.matchesFacilityDetail(facility));
    }

    /** {@code IN} 조회는 순서를 보장하지 않아, 점수 desc로 정렬된 id 순서를 다시 입혀준다. */
    private List<Facility> loadInOrder(List<Long> facilityIdsInOrder) {
        Map<Long, Facility> byId = facilityRepository.findAllById(facilityIdsInOrder).stream()
                .collect(Collectors.toMap(Facility::getFacilityId, facility -> facility));

        List<Facility> ordered = new ArrayList<>();
        for (Long facilityId : facilityIdsInOrder) {
            Facility facility = byId.get(facilityId);
            if (facility != null) {
                ordered.add(facility);
            }
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
