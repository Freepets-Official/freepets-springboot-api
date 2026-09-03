package com.freepets.domain.course.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petcheck.entity.PetCheckResult;
import com.freepets.domain.petcheck.service.PetCheckJudgeService;
import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;
import com.freepets.domain.petsatisfaction.repository.PetSatisfactionRepository;
import com.freepets.domain.review.entity.ReviewPet;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.entity.Tag;
import com.freepets.domain.review.repository.FacilityReviewAggregate;
import com.freepets.domain.review.repository.ReviewPetRepository;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.domain.review.repository.ReviewTagRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

// GET /api/v1/courses/similar — "취향 비슷한 새곳 탐험". 07-courses.md 공식 스펙(카테고리 가중3 +
// 태그 겹침수) 기준. similar-course-scoring.md의 확장 가중치(경험태그 가중2.0, kind/breedSize
// 보너스 등)는 합의 전까지 채택하지 않는다 — matchedByKind/matchedByBreedSize는 점수에는 안 쓰고
// 응답 표시(추천 근거)용으로만 계산한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseSimilarService {

    private static final int CATEGORY_MATCH_SCORE = 3;
    private static final int MINIMUM_CANDIDATE_COUNT = 2;
    private static final String PERSONALIZED_TITLE = "취향과 비슷한 새로운 곳";
    private static final String POPULAR_FALLBACK_TITLE = "지금 인기 있는 곳";

    private final PetRepository petRepository;
    private final PetSatisfactionRepository petSatisfactionRepository;
    private final FacilityRepository facilityRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewTagRepository reviewTagRepository;
    private final ReviewPetRepository reviewPetRepository;
    private final PetCheckJudgeService petCheckJudgeService;
    private final CourseAssemblyService courseAssemblyService;

    public CourseResponseDTO.SimilarCourseResult getSimilarCourse(
            Long userId,
            List<Long> petIds,
            Double maxDistanceM
    ) {
        double maxDistanceMeters = maxDistanceM != null ? maxDistanceM : CourseAssemblyService.DEFAULT_MAX_STOP_DISTANCE_METERS;
        List<Pet> pets = findOwnedPets(userId, petIds);

        // 1) 취향 프로필 — 좋아한(=만족도 기록을 남긴) 곳들의 category ∪ review tag.
        List<PetSatisfaction> satisfactions = petSatisfactionRepository.findAllByPetPetIdIn(petIds);
        Set<Long> likedFacilityIds = satisfactions.stream()
                .map(satisfaction -> satisfaction.getFacility().getFacilityId())
                .collect(Collectors.toSet());

        if (likedFacilityIds.isEmpty()) {
            return getPopularFallbackCourse(pets, maxDistanceMeters);
        }

        Set<FacilityCategory> likedCategories = satisfactions.stream()
                .map(satisfaction -> satisfaction.getFacility().getCategory())
                .collect(Collectors.toSet());
        Set<Tag> likedTags = reviewTagRepository.findDistinctTagsByFacilityIdIn(likedFacilityIds);

        // 2) 후보 풀 — 아직 안 가봤고, 카테고리 또는 태그가 하나라도 겹치는 시설.
        Map<Long, Facility> candidatesById = new HashMap<>();
        facilityRepository.findAllByIsActiveTrueAndPetAllowedNotAndFacilityIdNotInAndCategoryIn(
                PetAllowed.DENIED, likedFacilityIds, likedCategories
        ).forEach(facility -> candidatesById.put(facility.getFacilityId(), facility));

        if (!likedTags.isEmpty()) {
            List<Long> tagMatchedIds = reviewTagRepository.findFacilityIdsByTagInExcluding(likedTags, likedFacilityIds);
            facilityRepository.findAllById(tagMatchedIds).stream()
                    .filter(facility -> facility.isActive() && facility.getPetAllowed() != PetAllowed.DENIED)
                    .forEach(facility -> candidatesById.put(facility.getFacilityId(), facility));
        }

        // 3) "동반 가능"은 시설 단위 사실(petAllowed)이 아니라 실제 판별 기준 — 선택한 반려동물
        // 전체가 DENIED로 막히지 않는 시설만 남긴다.
        List<Facility> eligibleCandidates = candidatesById.values().stream()
                .filter(facility -> petCheckJudgeService.judgeGroup(pets, facility).overall() != PetCheckResult.DENIED)
                .toList();

        if (eligibleCandidates.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new GeneralException(ErrorStatus.COURSE4003);
        }

        // 4) 유사도 = 카테고리 일치(가중 3) + 태그 겹침 수, desc 정렬.
        Map<Long, List<Tag>> tagsByFacilityId = new HashMap<>();
        List<Facility> candidatesScoreDescSorted = eligibleCandidates.stream()
                .sorted(Comparator.comparingInt(
                        (Facility facility) -> similarityScore(facility, likedCategories, likedTags, tagsByFacilityId)
                ).reversed())
                .toList();

        // 5) 카테고리 다양성 + 거리 제약 + 동선 조립. 거리 제약 때문에 후보는 충분해도 실제
        // 채택된 스톱은 더 줄 수 있어 여기서 한 번 더 확인한다(3번의 사전 확인만으론 부족).
        List<Facility> stops = courseAssemblyService.assemble(candidatesScoreDescSorted, maxDistanceMeters);
        if (stops.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new GeneralException(ErrorStatus.COURSE4003);
        }

        List<CourseResponseDTO.SimilarStop> stopDtos = stops.stream()
                .map(facility -> toSimilarStop(facility, likedTags, tagsByFacilityId, pets))
                .toList();

        return new CourseResponseDTO.SimilarCourseResult(PERSONALIZED_TITLE, true, stopDtos);
    }

    /**
     * 취향 프로필(만족도 기록)이 아예 없는 신규 유저용 대체 경로 — 카테고리/태그로 매치할 재료가
     * 없으므로 개인화를 포기하고, 리뷰 평점 기준으로 "지금 인기 있는 곳"을 대신 추천한다. 실제
     * 동반 가능 여부(judgeGroup)는 그대로 검증한다.
     */
    private CourseResponseDTO.SimilarCourseResult getPopularFallbackCourse(
            List<Pet> pets,
            double maxDistanceMeters
    ) {
        List<Facility> candidates = facilityRepository.findAllByIsActiveTrueAndPetAllowedNot(PetAllowed.DENIED);

        List<Facility> eligibleCandidates = candidates.stream()
                .filter(facility -> petCheckJudgeService.judgeGroup(pets, facility).overall() != PetCheckResult.DENIED)
                .toList();

        if (eligibleCandidates.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new GeneralException(ErrorStatus.COURSE4003);
        }

        List<Long> candidateIds = eligibleCandidates.stream().map(Facility::getFacilityId).toList();
        Map<Long, Double> scoreById = reviewRepository.aggregateByFacilityIdIn(candidateIds, ReviewReportStatus.ACCEPTED).stream()
                .collect(Collectors.toMap(FacilityReviewAggregate::facilityId, FacilityReviewAggregate::averageScore));

        List<Facility> candidatesScoreDescSorted = eligibleCandidates.stream()
                .sorted(Comparator.comparingDouble(
                        (Facility facility) -> scoreById.getOrDefault(facility.getFacilityId(), 0.0)
                ).reversed())
                .toList();

        List<Facility> stops = courseAssemblyService.assemble(candidatesScoreDescSorted, maxDistanceMeters);
        if (stops.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new GeneralException(ErrorStatus.COURSE4003);
        }

        List<CourseResponseDTO.SimilarStop> stopDtos = stops.stream()
                .map(facility -> toPopularFallbackStop(facility, pets))
                .toList();

        return new CourseResponseDTO.SimilarCourseResult(POPULAR_FALLBACK_TITLE, false, stopDtos);
    }

    private CourseResponseDTO.SimilarStop toPopularFallbackStop(
            Facility facility,
            List<Pet> selectedPets
    ) {
        List<ReviewPet> reviewPets = reviewPetRepository
                .findAllByReview_Facility_FacilityIdAndReview_DeletedAtIsNull(facility.getFacilityId());
        Set<Kind> reviewedKinds = reviewPets.stream().map(reviewPet -> reviewPet.getPet().getKind()).collect(Collectors.toSet());
        boolean matchedByKind = selectedPets.stream().anyMatch(pet -> reviewedKinds.contains(pet.getKind()));

        return new CourseResponseDTO.SimilarStop(
                facility.getFacilityId(),
                facility.getName(),
                List.of(),
                matchedByKind,
                false,
                "아직 취향 데이터가 부족해서 지금 평점이 좋은 곳을 보여드려요"
        );
    }

    private int similarityScore(
            Facility facility,
            Set<FacilityCategory> likedCategories,
            Set<Tag> likedTags,
            Map<Long, List<Tag>> tagsByFacilityId
    ) {
        List<Tag> tags = tagsByFacilityId.computeIfAbsent(
                facility.getFacilityId(), reviewTagRepository::findTagsByFacilityId
        );

        int categoryScore = likedCategories.contains(facility.getCategory()) ? CATEGORY_MATCH_SCORE : 0;
        long tagOverlap = tags.stream().filter(likedTags::contains).count();

        return categoryScore + (int) tagOverlap;
    }

    private CourseResponseDTO.SimilarStop toSimilarStop(
            Facility facility,
            Set<Tag> likedTags,
            Map<Long, List<Tag>> tagsByFacilityId,
            List<Pet> selectedPets
    ) {
        List<Tag> matchedTags = tagsByFacilityId
                .getOrDefault(facility.getFacilityId(), List.of()).stream()
                .filter(likedTags::contains)
                .distinct()
                .toList();

        List<ReviewPet> reviewPets = reviewPetRepository
                .findAllByReview_Facility_FacilityIdAndReview_DeletedAtIsNull(facility.getFacilityId());
        Set<Kind> reviewedKinds = reviewPets.stream().map(reviewPet -> reviewPet.getPet().getKind()).collect(Collectors.toSet());
        Set<BreedSize> reviewedBreedSizes = reviewPets.stream()
                .map(reviewPet -> reviewPet.getPet().getBreedSize())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        boolean matchedByKind = selectedPets.stream().anyMatch(pet -> reviewedKinds.contains(pet.getKind()));
        boolean matchedByBreedSize = selectedPets.stream()
                .anyMatch(pet -> pet.getBreedSize() != null && reviewedBreedSizes.contains(pet.getBreedSize()));

        return new CourseResponseDTO.SimilarStop(
                facility.getFacilityId(),
                facility.getName(),
                matchedTags,
                matchedByKind,
                matchedByBreedSize,
                buildReason(matchedTags, matchedByKind)
        );
    }

    private String buildReason(
            List<Tag> matchedTags,
            boolean matchedByKind
    ) {
        if (matchedTags.isEmpty()) {
            return "좋아하신 카테고리와 비슷해요";
        }
        String tagPart = matchedTags.stream().map(Enum::name).collect(Collectors.joining(", "));
        return matchedByKind
                ? tagPart + "이 비슷해요 (같은 반려동물 리뷰 기준)"
                : tagPart + "이 비슷해요";
    }

    // CourseLikedService.findOwnedPets와 같은 이유 — petIds 중복 제거 후 소유 검증.
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
