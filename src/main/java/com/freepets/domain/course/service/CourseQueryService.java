package com.freepets.domain.course.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.course.converter.CourseConverter;
import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.Course;
import com.freepets.domain.course.entity.CourseSource;
import com.freepets.domain.course.repository.CourseRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;
import com.freepets.domain.petsatisfaction.repository.PetSatisfactionRepository;
import com.freepets.domain.review.entity.Tag;
import com.freepets.domain.review.repository.FacilityPetProfile;
import com.freepets.domain.review.repository.FacilityTag;
import com.freepets.domain.review.repository.ReviewPetRepository;
import com.freepets.domain.review.repository.ReviewTagRepository;

import lombok.RequiredArgsConstructor;

// GET /api/v1/courses — 내 코스(CUSTOM) 목록. MVP는 페이지네이션 없이 flat list
// (07-courses.md "결정된 사항" 참고).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseQueryService {

    private final CourseRepository courseRepository;
    private final PetRepository petRepository;
    private final PetSatisfactionRepository petSatisfactionRepository;
    private final ReviewTagRepository reviewTagRepository;
    private final ReviewPetRepository reviewPetRepository;

    /**
     * 개인화 채점(CourseSimilarService와 같은 카테고리/태그/종/크기 가중치)에 쓸 후보 풀 크기 —
     * DenialReportQueryService.MAX_CHECKED_FACILITIES와 같은 이유로, 공개 코스가 계속 늘어도
     * 채점 비용을 이 크기로 고정한다. 다만 이 값은 "최신 N개 중에서 재정렬"이라 최신 코스가
     * 이 수보다 많아지면 그보다 오래된 코스는 아무리 취향이 잘 맞아도 이 목록에 안 뜬다(페이지네이션
     * total은 전체 개수라 이 한계와 어긋날 수 있음) — 실무상 "최근 공개된 코스 중 취향에 맞는 것"을
     * 보여주는 목적엔 맞고, 필요해지면 후보 자체를 유사도 기준으로 DB에서 미리 좁히는 방식으로
     * 바꿔야 한다.
     */
    private static final int PERSONALIZATION_CANDIDATE_POOL_SIZE = 200;

    private static final double CATEGORY_MATCH_SCORE = 3.0;
    private static final double KIND_MATCH_BONUS = 0.5;
    private static final double BREED_SIZE_MATCH_BONUS = 0.2;

    public List<CourseResponseDTO.MyCourse> getMyCourses(Long userId) {
        return courseRepository.findAllByUser_Id(userId).stream()
                .map(CourseConverter::toMyCourse)
                .toList();
    }

    /**
     * GET /api/v1/courses/public — 다른 사용자가 공개한 CUSTOM 코스 둘러보기. 전체 사용자
     * 대상이라 내 코스와 달리 페이지네이션이 필요하다(위 getMyCourses 주석 참고).
     *
     * <p>기준 없이 최신순으로만 보여주면 공개 코스가 늘어날수록 나와 무관한 코스에 묻히므로,
     * 로그인 상태고 반려동물의 취향 데이터(만족도 기록)가 있으면 그 반려동물이 좋아한 카테고리·
     * 태그와 코스 스톱들의 평균 매치 점수로 정렬한다(CourseSimilarService의 채점 방식을 코스
     * 단위로 옮긴 것). userId가 없거나(비로그인) 취향 데이터가 없는 신규 유저는 "많이 담아간
     * 코스"(copyCount) 순으로 대신 보여준다.
     */
    public CourseResponseDTO.PublicCourseResult getPublicCourses(
            Long userId,
            Pageable pageable
    ) {
        List<Pet> pets = userId == null
                ? List.of()
                : petRepository.findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(userId);
        if (pets.isEmpty()) {
            return getPopularPublicCourses(pageable);
        }

        List<Long> petIds = pets.stream().map(Pet::getPetId).toList();
        List<PetSatisfaction> satisfactions = petSatisfactionRepository.findAllByPetPetIdIn(petIds);
        if (satisfactions.isEmpty()) {
            return getPopularPublicCourses(pageable);
        }

        return getPersonalizedPublicCourses(satisfactions, pets, pageable);
    }

    private CourseResponseDTO.PublicCourseResult getPopularPublicCourses(Pageable pageable) {
        Page<Course> page = courseRepository.findAllBySourceAndIsPublicTrueOrderByCopyCountDescCreatedAtDesc(
                CourseSource.CUSTOM, pageable
        );
        List<CourseResponseDTO.PublicCourse> items = page.getContent().stream()
                .map(CourseConverter::toPublicCourse)
                .toList();

        return new CourseResponseDTO.PublicCourseResult(items, page.getTotalElements());
    }

    private CourseResponseDTO.PublicCourseResult getPersonalizedPublicCourses(
            List<PetSatisfaction> satisfactions,
            List<Pet> pets,
            Pageable pageable
    ) {
        Set<FacilityCategory> likedCategories = satisfactions.stream()
                .map(satisfaction -> satisfaction.getFacility().getCategory())
                .collect(Collectors.toSet());
        Set<Long> likedFacilityIds = satisfactions.stream()
                .map(satisfaction -> satisfaction.getFacility().getFacilityId())
                .collect(Collectors.toSet());
        Set<Tag> likedTags = reviewTagRepository.findDistinctTagsByFacilityIdIn(likedFacilityIds);

        Page<Course> candidatePage = courseRepository.findAllBySourceAndIsPublicTrueOrderByCreatedAtDesc(
                CourseSource.CUSTOM, PageRequest.of(0, PERSONALIZATION_CANDIDATE_POOL_SIZE)
        );
        List<Course> candidates = candidatePage.getContent();

        List<Long> stopFacilityIds = candidates.stream()
                .flatMap(course -> course.getStops().stream())
                .map(stop -> stop.getFacility().getFacilityId())
                .distinct()
                .toList();
        Map<Long, List<Tag>> tagsByFacilityId = reviewTagRepository.findTagsByFacilityIdIn(stopFacilityIds).stream()
                .collect(Collectors.groupingBy(FacilityTag::facilityId,
                        Collectors.mapping(FacilityTag::tag, Collectors.toList())));
        List<FacilityPetProfile> petProfiles = reviewPetRepository.findKindAndBreedSizeByFacilityIdIn(stopFacilityIds);
        Map<Long, Set<Kind>> kindsByFacilityId = petProfiles.stream()
                .collect(Collectors.groupingBy(FacilityPetProfile::facilityId,
                        Collectors.mapping(FacilityPetProfile::kind, Collectors.toSet())));
        Map<Long, Set<BreedSize>> breedSizesByFacilityId = petProfiles.stream()
                .filter(profile -> profile.breedSize() != null)
                .collect(Collectors.groupingBy(FacilityPetProfile::facilityId,
                        Collectors.mapping(FacilityPetProfile::breedSize, Collectors.toSet())));

        List<Course> sorted = candidates.stream()
                .sorted(Comparator
                        .comparingDouble((Course course) -> relevanceScore(
                                course, likedCategories, likedTags, tagsByFacilityId,
                                kindsByFacilityId, breedSizesByFacilityId, pets
                        )).reversed()
                        .thenComparing(Course::getCreatedAt, Comparator.reverseOrder()))
                .toList();

        List<Course> pageContent = paginate(sorted, pageable);
        List<CourseResponseDTO.PublicCourse> items = pageContent.stream()
                .map(CourseConverter::toPublicCourse)
                .toList();

        return new CourseResponseDTO.PublicCourseResult(items, candidatePage.getTotalElements());
    }

    private List<Course> paginate(
            List<Course> sorted,
            Pageable pageable
    ) {
        int fromIndex = Math.min((int) pageable.getOffset(), sorted.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), sorted.size());
        return sorted.subList(fromIndex, toIndex);
    }

    // "스톱들 평균" — 코스 하나의 매력도를 스톱 하나의 점수로 대표시키지 않고, 스톱 전체의
    // 평균으로 본다. 스톱이 하나도 없으면(이론상 발생하지 않지만 방어적으로) 0점.
    private double relevanceScore(
            Course course,
            Set<FacilityCategory> likedCategories,
            Set<Tag> likedTags,
            Map<Long, List<Tag>> tagsByFacilityId,
            Map<Long, Set<Kind>> kindsByFacilityId,
            Map<Long, Set<BreedSize>> breedSizesByFacilityId,
            List<Pet> pets
    ) {
        List<Facility> stopFacilities = course.getStops().stream()
                .map(stop -> stop.getFacility())
                .toList();
        if (stopFacilities.isEmpty()) {
            return 0;
        }

        double totalScore = stopFacilities.stream()
                .mapToDouble(facility -> stopScore(
                        facility, likedCategories, likedTags, tagsByFacilityId,
                        kindsByFacilityId, breedSizesByFacilityId, pets
                ))
                .sum();

        return totalScore / stopFacilities.size();
    }

    // CourseSimilarService.similarityScore와 같은 채점 방식(카테고리 매치 + 태그 겹침 + 종/크기
    // 매치 보너스) — 코스 단위로 옮기며 다시 구현했다. CourseSimilarService는 "판별까지 통과한
    // 시설 후보"를 다루는 무거운 흐름(judgeGroup 호출 등)이라 여길 위해 그 서비스 자체를 고쳐
    // 끌어쓰기보다, 순수 계산 부분만 이쪽에 맞게 다시 작성하는 편이 기존 로직(테스트로 이미
    // 검증된)을 건드릴 위험이 적다.
    private double stopScore(
            Facility facility,
            Set<FacilityCategory> likedCategories,
            Set<Tag> likedTags,
            Map<Long, List<Tag>> tagsByFacilityId,
            Map<Long, Set<Kind>> kindsByFacilityId,
            Map<Long, Set<BreedSize>> breedSizesByFacilityId,
            List<Pet> pets
    ) {
        double categoryScore = likedCategories.contains(facility.getCategory()) ? CATEGORY_MATCH_SCORE : 0;

        List<Tag> tags = tagsByFacilityId.getOrDefault(facility.getFacilityId(), List.of());
        long tagOverlap = tags.stream().filter(likedTags::contains).count();
        double tagScore = tagOverlapScore((int) tagOverlap);

        Set<Kind> reviewedKinds = kindsByFacilityId.getOrDefault(facility.getFacilityId(), Set.of());
        boolean isMatchedByKind = pets.stream().anyMatch(pet -> reviewedKinds.contains(pet.getKind()));
        double kindScore = isMatchedByKind ? KIND_MATCH_BONUS : 0;

        Set<BreedSize> reviewedBreedSizes = breedSizesByFacilityId.getOrDefault(facility.getFacilityId(), Set.of());
        boolean isMatchedByBreedSize = pets.stream()
                .anyMatch(pet -> pet.getBreedSize() != null && reviewedBreedSizes.contains(pet.getBreedSize()));
        double breedSizeScore = isMatchedByBreedSize ? BREED_SIZE_MATCH_BONUS : 0;

        return categoryScore + tagScore + kindScore + breedSizeScore;
    }

    // CourseSimilarService.tagOverlapScore와 같은 이유 — 태그가 여러 개 겹칠수록 가중치를
    // 등차가 아니라 더 크게 준다.
    private double tagOverlapScore(int overlapCount) {
        if (overlapCount <= 0) {
            return 0;
        }
        return 0.1 + 0.025 * (overlapCount - 1) * overlapCount;
    }

}
