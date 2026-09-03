package com.freepets.domain.course.service;

import java.util.Comparator;
import java.util.HashMap;
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
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.entity.Tag;
import com.freepets.domain.review.repository.FacilityPetProfile;
import com.freepets.domain.review.repository.FacilityReviewAggregate;
import com.freepets.domain.review.repository.FacilityTag;
import com.freepets.domain.review.repository.ReviewPetRepository;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.domain.review.repository.ReviewTagRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.global.util.GeoUtils;

import lombok.RequiredArgsConstructor;

/**
 * GET /api/v1/courses/similar — "취향 비슷한 새곳 탐험". 07-courses.md 공식 스펙(카테고리 가중3 +
 * 태그 겹침수)에 similar-course-scoring.md의 확장 가중치(kind/breedSize 보너스, 태그 다중 겹침
 * 가속)를 합의해 반영했다.
 *
 * <p>점수 = 카테고리 매치({@link #CATEGORY_MATCH_SCORE}) + 태그 겹침 점수({@link
 * #tagOverlapScore}) + 종 매치 보너스({@link #KIND_MATCH_BONUS}) + 크기 매치 보너스({@link
 * #BREED_SIZE_MATCH_BONUS}). 넷 다 "가점만" 준다 — 안 맞는다고 감점하지 않는다(다른 종이라고
 * 추천에서 배제하면 안 된다는 요구사항).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseSimilarService {

    private static final double CATEGORY_MATCH_SCORE = 3.0;

    /** 같은 종이면 "조금 높게" — 카테고리 매치보다는 한참 작은 보너스. */
    private static final double KIND_MATCH_BONUS = 0.5;

    /** 크기(체중대) 매치는 종 매치보다 낮은 가중치. */
    private static final double BREED_SIZE_MATCH_BONUS = 0.2;

    private static final int MINIMUM_CANDIDATE_COUNT = 2;

    /**
     * 실제 판별(judgeGroup)은 후보 하나하나에 대해 여러 규칙을 도는 무거운 호출이라, 후보 전체를
     * 다 판별하면 후보가 많아질수록(특히 취향 프로필이 없어 필터가 느슨한 대체 경로) 응답이
     * 느려진다. 점수(DB 부하가 가벼운 배치 조회로 계산 가능)로 먼저 추려 상위 N개만 판별한다 —
     * 최종 스톱 수(4곳) 대비 넉넉한 여유를 두면서도 판별 호출 수를 DB 규모와 무관하게 고정한다.
     * 상위 N 안에서 판별 탈락이 몰리면 이론상 후보가 실제보다 적게 잡힐 수 있지만, 40이면 실무상
     * 거의 발생하지 않는다.
     */
    private static final int CANDIDATE_JUDGE_LIMIT = 40;

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
            CourseDistanceOption maxDistanceM,
            String sido,
            String sigungu,
            Set<CourseTheme> themes
    ) {
        double maxDistanceMeters = (maxDistanceM != null ? maxDistanceM : CourseDistanceOption.FIVE_KM).getMeters();
        List<Pet> pets = findOwnedPets(userId, petIds);

        // 1) 취향 프로필 — 좋아한(=만족도 기록을 남긴) 곳들의 category ∪ review tag.
        List<PetSatisfaction> satisfactions = petSatisfactionRepository.findAllByPetPetIdIn(petIds);
        Set<Long> likedFacilityIds = satisfactions.stream()
                .map(satisfaction -> satisfaction.getFacility().getFacilityId())
                .collect(Collectors.toSet());

        if (likedFacilityIds.isEmpty()) {
            return getPopularFallbackCourse(pets, maxDistanceMeters, sido, sigungu, themes);
        }

        Set<FacilityCategory> likedCategories = satisfactions.stream()
                .map(satisfaction -> satisfaction.getFacility().getCategory())
                .collect(Collectors.toSet());
        Set<Tag> likedTags = reviewTagRepository.findDistinctTagsByFacilityIdIn(likedFacilityIds);
        // 만족도 점수가 가장 높은 좋아한 시설 하나 — 동점 후보를 추릴 때 이 지점 근처를 우선하는
        // 기준점으로 쓴다. "가장 가까운 좋아한 시설"(여러 개 중 최소 거리)로 하면 좋아한 시설
        // 자체가 전국에 흩어져 있을 때 후보도 그만큼 흩어진 채 뽑혀서(각자 가장 가까운 곳 근처로),
        // 정작 후보끼리는 가까워지지 않는 문제가 있었다 — 기준점을 하나로 고정해야 후보들이
        // 한 지역으로 모인다.
        Facility likedAnchor = satisfactions.stream()
                .filter(satisfaction -> satisfaction.getFacility().getLat() != null
                        && satisfaction.getFacility().getLng() != null)
                .max(Comparator.comparingDouble(PetSatisfaction::getScore))
                .map(PetSatisfaction::getFacility)
                .orElse(null);

        // 2) 후보 풀 — 아직 안 가봤고, 카테고리 또는 태그가 하나라도 겹치는 시설. 지역·테마
        // 필터(선택 사항)를 여기서 먼저 걸러 이후 배치 조회·판별 대상을 줄인다.
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

        List<Facility> regionFilteredCandidates = candidatesById.values().stream()
                .filter(facility -> matchesRegionAndTheme(facility, sido, sigungu, themes))
                .filter(facility -> isWithinAnchorDistance(facility, likedAnchor, maxDistanceMeters))
                .toList();

        if (regionFilteredCandidates.isEmpty()) {
            throw new GeneralException(ErrorStatus.COURSE4003);
        }

        // 3) 점수 계산에 필요한 태그·반려동물 프로필을 후보 전체에 대해 한 번에 배치 조회한다
        // (후보마다 따로 조회하면 후보 수만큼 쿼리가 늘어나는 N+1이라 여기서 막는다).
        List<Long> candidateIds = regionFilteredCandidates.stream().map(Facility::getFacilityId).toList();
        Map<Long, List<Tag>> tagsByFacilityId = reviewTagRepository.findTagsByFacilityIdIn(candidateIds).stream()
                .collect(Collectors.groupingBy(FacilityTag::facilityId,
                        Collectors.mapping(FacilityTag::tag, Collectors.toList())));
        PetProfilesByFacility petProfiles = loadPetProfiles(candidateIds);

        // 4) 유사도 = 카테고리 매치 + 태그 겹침(가속) + 종/크기 매치 보너스, desc 정렬. 점수가
        // 같으면(리뷰·태그가 없는 시설은 대부분 카테고리 매치 점수만 갖는다) 기준점(likedAnchor)과
        // 가까운 후보를 우선한다 — 안 그러면 동점자 사이에서 뭐가 뽑히는지가 지역과 무관해져,
        // 상위 CANDIDATE_JUDGE_LIMIT개가 전국에 흩어진 채로 뽑혀 거리 조립(6번)에서 다 걸러지는
        // 문제가 있었다.
        List<Facility> candidatesScoreDescSorted = regionFilteredCandidates.stream()
                .sorted(Comparator
                        .comparingDouble((Facility facility) -> similarityScore(
                                facility, likedCategories, likedTags, tagsByFacilityId, petProfiles, pets
                        )).reversed()
                        .thenComparingDouble(facility -> distanceToAnchor(facility, likedAnchor)))
                .toList();

        // 5) "동반 가능"은 시설 단위 사실(petAllowed)이 아니라 실제 판별 기준 — 이 호출이 무거워
        // 점수 상위 CANDIDATE_JUDGE_LIMIT개만 판별한다(클래스 주석 참고).
        List<Facility> eligibleCandidates = candidatesScoreDescSorted.stream()
                .limit(CANDIDATE_JUDGE_LIMIT)
                .filter(facility -> petCheckJudgeService.judgeGroup(pets, facility).overall() != PetCheckResult.DENIED)
                .toList();

        if (eligibleCandidates.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new GeneralException(ErrorStatus.COURSE4003);
        }

        // 6) 카테고리 다양성 + 거리 제약 + 동선 조립. 거리 제약 때문에 후보는 충분해도 실제
        // 채택된 스톱은 더 줄 수 있어 여기서 한 번 더 확인한다(5번의 사전 확인만으론 부족).
        List<Facility> stops = courseAssemblyService.assemble(eligibleCandidates, maxDistanceMeters);
        if (stops.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new GeneralException(ErrorStatus.COURSE4003);
        }

        List<CourseResponseDTO.SimilarStop> stopDtos = stops.stream()
                .map(facility -> toSimilarStop(facility, likedTags, tagsByFacilityId, petProfiles, pets))
                .toList();

        return new CourseResponseDTO.SimilarCourseResult(PERSONALIZED_TITLE, true, stopDtos);
    }

    /**
     * 취향 프로필(만족도 기록)이 아예 없는 신규 유저용 대체 경로 — 카테고리/태그로 매치할 재료가
     * 없으므로 개인화를 포기하고, 리뷰 평점 기준으로 "지금 인기 있는 곳"을 대신 추천한다. 실제
     * 동반 가능 여부(judgeGroup)는 그대로 검증하되, 점수 상위 CANDIDATE_JUDGE_LIMIT개만 판별한다
     * (전체 활성 시설을 다 판별하면 이 경로가 특히 느려졌다).
     */
    private CourseResponseDTO.SimilarCourseResult getPopularFallbackCourse(
            List<Pet> pets,
            double maxDistanceMeters,
            String sido,
            String sigungu,
            Set<CourseTheme> themes
    ) {
        List<Facility> regionFilteredCandidates = facilityRepository
                .findAllByIsActiveTrueAndPetAllowedNot(PetAllowed.DENIED).stream()
                .filter(facility -> matchesRegionAndTheme(facility, sido, sigungu, themes))
                .toList();

        if (regionFilteredCandidates.isEmpty()) {
            throw new GeneralException(ErrorStatus.COURSE4003);
        }

        List<Long> candidateIds = regionFilteredCandidates.stream().map(Facility::getFacilityId).toList();
        Map<Long, Double> scoreById = reviewRepository.aggregateByFacilityIdIn(candidateIds, ReviewReportStatus.ACCEPTED).stream()
                .collect(Collectors.toMap(FacilityReviewAggregate::facilityId, FacilityReviewAggregate::averageScore));

        List<Facility> candidatesScoreDescSorted = regionFilteredCandidates.stream()
                .sorted(Comparator.comparingDouble(
                        (Facility facility) -> scoreById.getOrDefault(facility.getFacilityId(), 0.0)
                ).reversed())
                .toList();

        List<Facility> topScored = candidatesScoreDescSorted.stream().limit(CANDIDATE_JUDGE_LIMIT).toList();
        PetProfilesByFacility petProfiles = loadPetProfiles(topScored.stream().map(Facility::getFacilityId).toList());

        List<Facility> eligibleCandidates = topScored.stream()
                .filter(facility -> petCheckJudgeService.judgeGroup(pets, facility).overall() != PetCheckResult.DENIED)
                .toList();

        if (eligibleCandidates.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new GeneralException(ErrorStatus.COURSE4003);
        }

        List<Facility> stops = courseAssemblyService.assemble(eligibleCandidates, maxDistanceMeters);
        if (stops.size() < MINIMUM_CANDIDATE_COUNT) {
            throw new GeneralException(ErrorStatus.COURSE4003);
        }

        List<CourseResponseDTO.SimilarStop> stopDtos = stops.stream()
                .map(facility -> toPopularFallbackStop(facility, petProfiles, pets))
                .toList();

        return new CourseResponseDTO.SimilarCourseResult(POPULAR_FALLBACK_TITLE, false, stopDtos);
    }

    private CourseResponseDTO.SimilarStop toPopularFallbackStop(
            Facility facility,
            PetProfilesByFacility petProfiles,
            List<Pet> selectedPets
    ) {
        boolean matchedByKind = isMatchedByKind(facility, petProfiles, selectedPets);

        return new CourseResponseDTO.SimilarStop(
                facility.getFacilityId(),
                facility.getName(),
                List.of(),
                matchedByKind,
                false,
                "아직 취향 데이터가 부족해서 지금 평점이 좋은 곳을 보여드려요"
        );
    }

    // CourseLikedService.matchesRegionAndTheme와 같은 이유 — sido/sigungu/themes 셋 다 선택
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

    /**
     * 후보가 기준점(likedAnchor)의 maxDistanceMeters 안에 있는지 — 점수만으로 거르면, 점수가
     * 조금이라도 더 높은 후보 하나가(태그 하나 겹침 등으로) 기준점에서 수백km 떨어진 곳이어도
     * 정렬 맨 앞에 서게 된다. 그러면 CourseAssemblyService의 조립이 그 후보를 동선의 시작점으로
     * 삼는데, 근처에 아무것도 없으니 스톱이 1개로 붕괴한다(실제로 겪은 문제) — 동점 정렬 힌트로는
     * 못 막고, 애초에 후보 풀에서 걸러내야 한다. 기준점이 없으면(좌표 있는 좋아한 시설이 하나도
     * 없음) 거르지 않는다.
     */
    private boolean isWithinAnchorDistance(
            Facility candidate,
            Facility likedAnchor,
            double maxDistanceMeters
    ) {
        if (likedAnchor == null) {
            return true;
        }
        if (candidate.getLat() == null || candidate.getLng() == null) {
            return false;
        }
        return GeoUtils.distanceMeters(
                likedAnchor.getLat(), likedAnchor.getLng(), candidate.getLat(), candidate.getLng()
        ) <= maxDistanceMeters;
    }

    /**
     * 기준점(likedAnchor)까지의 거리(m) — 이미 anchor 반경 안으로 걸러진 후보들 사이에서, 점수가
     * 같을 때(리뷰·태그가 없는 시설은 대부분 카테고리 매치 점수만 갖는다) 더 가까운 쪽을
     * 우선하는 정렬 보정용.
     */
    private double distanceToAnchor(
            Facility candidate,
            Facility likedAnchor
    ) {
        if (likedAnchor == null || candidate.getLat() == null || candidate.getLng() == null) {
            return Double.MAX_VALUE;
        }
        return GeoUtils.distanceMeters(
                likedAnchor.getLat(), likedAnchor.getLng(), candidate.getLat(), candidate.getLng()
        );
    }

    private double similarityScore(
            Facility facility,
            Set<FacilityCategory> likedCategories,
            Set<Tag> likedTags,
            Map<Long, List<Tag>> tagsByFacilityId,
            PetProfilesByFacility petProfiles,
            List<Pet> selectedPets
    ) {
        double categoryScore = likedCategories.contains(facility.getCategory()) ? CATEGORY_MATCH_SCORE : 0;

        List<Tag> tags = tagsByFacilityId.getOrDefault(facility.getFacilityId(), List.of());
        long tagOverlap = tags.stream().filter(likedTags::contains).count();
        double tagScore = tagOverlapScore((int) tagOverlap);

        double kindScore = isMatchedByKind(facility, petProfiles, selectedPets) ? KIND_MATCH_BONUS : 0;
        double breedSizeScore = isMatchedByBreedSize(facility, petProfiles, selectedPets) ? BREED_SIZE_MATCH_BONUS : 0;

        return categoryScore + tagScore + kindScore + breedSizeScore;
    }

    /**
     * 태그가 겹칠수록 가중치를 등차가 아니라 더 크게 준다 — 태그 여러 개가 한꺼번에 겹치는 건
     * 하나씩 겹치는 것의 단순 합보다 취향이 더 잘 맞는다는 신호로 본다. 1개 겹치면 0.1, 2개
     * 0.15(+0.05), 3개 0.25(+0.10)로 겹칠수록 증가폭 자체가 커진다. 안 겹치면(0개) 보너스 없음
     * — 이 항은 "가점"이지 감점이 아니다.
     */
    private double tagOverlapScore(int overlapCount) {
        if (overlapCount <= 0) {
            return 0;
        }
        return 0.1 + 0.025 * (overlapCount - 1) * overlapCount;
    }

    private boolean isMatchedByKind(
            Facility facility,
            PetProfilesByFacility petProfiles,
            List<Pet> selectedPets
    ) {
        Set<Kind> reviewedKinds = petProfiles.kindsByFacilityId().getOrDefault(facility.getFacilityId(), Set.of());
        return selectedPets.stream().anyMatch(pet -> reviewedKinds.contains(pet.getKind()));
    }

    private boolean isMatchedByBreedSize(
            Facility facility,
            PetProfilesByFacility petProfiles,
            List<Pet> selectedPets
    ) {
        Set<BreedSize> reviewedBreedSizes = petProfiles.breedSizesByFacilityId().getOrDefault(facility.getFacilityId(), Set.of());
        return selectedPets.stream().anyMatch(pet -> pet.getBreedSize() != null && reviewedBreedSizes.contains(pet.getBreedSize()));
    }

    /** facilityIds 전체의 (종, 크기) 프로필을 한 번에 배치 조회해 시설별로 그룹핑한다. */
    private PetProfilesByFacility loadPetProfiles(List<Long> facilityIds) {
        if (facilityIds.isEmpty()) {
            return new PetProfilesByFacility(Map.of(), Map.of());
        }

        List<FacilityPetProfile> profiles = reviewPetRepository.findKindAndBreedSizeByFacilityIdIn(facilityIds);

        Map<Long, Set<Kind>> kindsByFacilityId = profiles.stream()
                .collect(Collectors.groupingBy(FacilityPetProfile::facilityId,
                        Collectors.mapping(FacilityPetProfile::kind, Collectors.toSet())));
        Map<Long, Set<BreedSize>> breedSizesByFacilityId = profiles.stream()
                .filter(profile -> profile.breedSize() != null)
                .collect(Collectors.groupingBy(FacilityPetProfile::facilityId,
                        Collectors.mapping(FacilityPetProfile::breedSize, Collectors.toSet())));

        return new PetProfilesByFacility(kindsByFacilityId, breedSizesByFacilityId);
    }

    /** loadPetProfiles의 배치 조회 결과 — facilityId별 리뷰 반려동물의 종/크기 집합. */
    private record PetProfilesByFacility(
            Map<Long, Set<Kind>> kindsByFacilityId,
            Map<Long, Set<BreedSize>> breedSizesByFacilityId
    ) {}

    private CourseResponseDTO.SimilarStop toSimilarStop(
            Facility facility,
            Set<Tag> likedTags,
            Map<Long, List<Tag>> tagsByFacilityId,
            PetProfilesByFacility petProfiles,
            List<Pet> selectedPets
    ) {
        List<Tag> matchedTags = tagsByFacilityId
                .getOrDefault(facility.getFacilityId(), List.of()).stream()
                .filter(likedTags::contains)
                .distinct()
                .toList();

        boolean matchedByKind = isMatchedByKind(facility, petProfiles, selectedPets);
        boolean matchedByBreedSize = isMatchedByBreedSize(facility, petProfiles, selectedPets);

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
