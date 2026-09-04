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
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.service.BoundingBox;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;
import com.freepets.domain.petsatisfaction.repository.FacilityAverageSatisfaction;
import com.freepets.domain.petsatisfaction.repository.PetSatisfactionRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.global.util.GeoUtils;

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
        // 마지막 한 자리는 근처 식사 스톱(RESTAURANT)으로 예약한다 — sido가 있으면 지역명으로,
        // 없어도 이 아이가 좋아한 시설 중 좌표 있는 곳(likedAnchor)이 있으면 그 주변으로 찾는다
        // (CourseSimilarService.fetchMealCandidatesByRegionOrAnchor와 같은 이유).
        // isMealStop 판정을 카테고리(RESTAURANT)로 하면 안 된다 — themes가 선택 사항이라 필터
        // 없이 호출하면 실제로 이 아이가 좋아한 식당도 candidatesScoreDescSorted에 정상적으로
        // 들어올 수 있는데(matchesRegionAndTheme 참고), 그런 경우까지 자동 삽입 식사 스톱으로
        // 오인해 진짜 avgSatisfaction/reasonPets를 지워버리는 버그가 있었다 — 대신 "실제 방문·평가
        // 기록에서 나온 후보였는지"를 ID로 정확히 구분한다.
        Set<Long> personalizedFacilityIds = candidatesScoreDescSorted.stream()
                .map(Facility::getFacilityId)
                .collect(Collectors.toSet());
        Facility likedAnchor = candidatesScoreDescSorted.stream()
                .filter(facility -> facility.getLat() != null && facility.getLng() != null)
                .findFirst()
                .orElse(null);
        List<Facility> mealCandidates = fetchMealCandidates(sido, sigungu, likedAnchor, maxDistanceMeters);
        List<Facility> stops = courseAssemblyService.assemble(candidatesScoreDescSorted, mealCandidates, maxDistanceMeters);
        if (stops.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new GeneralException(ErrorStatus.COURSE4002);
        }

        String title = pets.stream()
                .map(Pet::getName)
                .collect(Collectors.joining("·")) + "가 좋아한 곳";

        List<CourseResponseDTO.LikedStop> stopDtos = stops.stream()
                .map(facility -> toLikedStop(facility, averageScoreByFacilityId, byFacilityId, petIds, personalizedFacilityIds))
                .toList();

        return new CourseResponseDTO.LikedCourseResult(title, stopDtos);
    }

    private CourseResponseDTO.LikedStop toLikedStop(
            Facility facility,
            Map<Long, Double> averageScoreByFacilityId,
            Map<Long, List<PetSatisfaction>> byFacilityId,
            List<Long> petIds,
            Set<Long> personalizedFacilityIds
    ) {
        // 자동 삽입 식사 스톱은 이 아이가 실제로 방문·만족도 평가한 곳이 아니라
        // averageScoreByFacilityId/byFacilityId 둘 다에 값이 없다 — 그대로 조회하면 NPE.
        if (!personalizedFacilityIds.contains(facility.getFacilityId())) {
            return new CourseResponseDTO.LikedStop(facility.getFacilityId(), facility.getName(), facility.getCategory(), true, 0.0, List.of());
        }

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
                false,
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
        // sido 없이 sigungu만 오면 무시한다(컨트롤러 문서 "sido 없이는 무시된다"와 일치시킴) —
        // "고성군"처럼 서로 다른 시/도(강원특별자치도·경상남도)에 같은 이름의 시/군/구가 실제로
        // 있어서, sido 없이 sigungu만으로 걸러내면 엉뚱한 지역의 동명 시/군/구까지 섞여 들어오는
        // 버그가 있었다.
        if (sido != null && sigungu != null && !sigungu.equals(facility.getSigungu())) {
            return false;
        }
        return themes == null || themes.isEmpty()
                || themes.stream().anyMatch(theme -> theme.matchesFacilityDetail(facility));
    }

    // CourseSimilarService.fetchMealCandidatesByRegionOrAnchor와 같은 이유 — sido가 있으면
    // 지역명으로, 없어도 anchor(likedAnchor) 좌표가 있으면 경계 사각형으로 찾는다. 둘 다 없으면
    // 빈 리스트(식사 스톱 없이 진행).
    private List<Facility> fetchMealCandidates(
            String sido,
            String sigungu,
            Facility anchor,
            double maxDistanceMeters
    ) {
        if (sido != null) {
            return facilityRepository.findPresetCandidates(sido, sigungu, Set.of(FacilityCategory.RESTAURANT)).stream()
                    .filter(facility -> isWithinAnchorDistance(facility, anchor, maxDistanceMeters))
                    .toList();
        }

        if (anchor == null) {
            return List.of();
        }

        BoundingBox boundingBox = BoundingBox.around(
                anchor.getLat().doubleValue(), anchor.getLng().doubleValue(), (int) maxDistanceMeters
        );
        return facilityRepository.findByCategoryWithinBoundingBox(
                        FacilityCategory.RESTAURANT,
                        boundingBox.minimumLatitude(), boundingBox.maximumLatitude(),
                        boundingBox.minimumLongitude(), boundingBox.maximumLongitude()
                ).stream()
                .filter(facility -> isWithinAnchorDistance(facility, anchor, maxDistanceMeters))
                .toList();
    }

    // CourseSimilarService.isWithinAnchorDistance와 같은 이유 — anchor가 없으면 거르지 않는다.
    private boolean isWithinAnchorDistance(
            Facility candidate,
            Facility anchor,
            double maxDistanceMeters
    ) {
        if (anchor == null) {
            return true;
        }
        if (candidate.getLat() == null || candidate.getLng() == null) {
            return false;
        }
        return GeoUtils.distanceMeters(
                anchor.getLat(), anchor.getLng(), candidate.getLat(), candidate.getLng()
        ) <= maxDistanceMeters;
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
