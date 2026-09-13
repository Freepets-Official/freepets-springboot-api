package com.freepets.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.Course;
import com.freepets.domain.course.entity.CourseSource;
import com.freepets.domain.course.repository.CourseRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;
import com.freepets.domain.petsatisfaction.repository.PetSatisfactionRepository;
import com.freepets.domain.review.repository.ReviewPetRepository;
import com.freepets.domain.review.repository.ReviewTagRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class CourseQueryServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private PetRepository petRepository;

    @Mock
    private PetSatisfactionRepository petSatisfactionRepository;

    @Mock
    private ReviewTagRepository reviewTagRepository;

    @Mock
    private ReviewPetRepository reviewPetRepository;

    @InjectMocks
    private CourseQueryService courseQueryService;

    @Test
    void 내_코스_목록을_스톱_순서대로_반환한다() {
        User user = createUser(1L, "테스터");

        Facility a = facility(1L, "A");
        Facility b = facility(2L, "B");
        Course course = Course.builder()
                .user(user)
                .name("몽이 코스")
                .source(CourseSource.CUSTOM)
                .build();
        course.replaceStops(List.of(a, b));
        ReflectionTestUtils.setField(course, "courseId", 10L);

        when(courseRepository.findAllByUser_Id(1L)).thenReturn(List.of(course));

        List<CourseResponseDTO.MyCourse> result = courseQueryService.getMyCourses(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).stopIds()).containsExactly(1L, 2L);
    }

    @Test
    void 비로그인이면_인기순으로_공개_코스를_반환한다() {
        User owner = createUser(1L, "테스터");
        Course course = publicCourse(owner, 10L, facility(1L, "A"));

        PageRequest pageable = PageRequest.of(0, 15);
        when(courseRepository.findAllBySourceAndIsPublicTrueOrderByCopyCountDescCreatedAtDesc(CourseSource.CUSTOM, pageable))
                .thenReturn(new PageImpl<>(List.of(course), pageable, 1));

        CourseResponseDTO.PublicCourseResult result = courseQueryService.getPublicCourses(null, pageable);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().get(0).ownerNickname()).isEqualTo("테스터");
        assertThat(result.items().get(0).stopIds()).containsExactly(1L);
    }

    @Test
    void 로그인했지만_반려동물이_없으면_인기순으로_폴백한다() {
        User owner = createUser(1L, "테스터");
        Course course = publicCourse(owner, 10L, facility(1L, "A"));

        PageRequest pageable = PageRequest.of(0, 15);
        when(petRepository.findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(2L)).thenReturn(List.of());
        when(courseRepository.findAllBySourceAndIsPublicTrueOrderByCopyCountDescCreatedAtDesc(CourseSource.CUSTOM, pageable))
                .thenReturn(new PageImpl<>(List.of(course), pageable, 1));

        CourseResponseDTO.PublicCourseResult result = courseQueryService.getPublicCourses(2L, pageable);

        assertThat(result.items()).hasSize(1);
    }

    @Test
    void 만족도_기록이_없으면_인기순으로_폴백한다() {
        User owner = createUser(1L, "테스터");
        Course course = publicCourse(owner, 10L, facility(1L, "A"));
        Pet pet = pet(5L, Kind.DOG);

        PageRequest pageable = PageRequest.of(0, 15);
        when(petRepository.findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(2L)).thenReturn(List.of(pet));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L))).thenReturn(List.of());
        when(courseRepository.findAllBySourceAndIsPublicTrueOrderByCopyCountDescCreatedAtDesc(CourseSource.CUSTOM, pageable))
                .thenReturn(new PageImpl<>(List.of(course), pageable, 1));

        CourseResponseDTO.PublicCourseResult result = courseQueryService.getPublicCourses(2L, pageable);

        assertThat(result.items()).hasSize(1);
    }

    @Test
    void 취향이_맞는_공개_코스가_먼저_오도록_개인화_정렬한다() {
        User owner = createUser(1L, "테스터");
        Pet pet = pet(5L, Kind.DOG);

        // 좋아한 시설(CAFE) — 이 유저의 취향 프로필 재료.
        Facility likedFacility = facility(100L, "좋아한 카페");
        ReflectionTestUtils.setField(likedFacility, "category", FacilityCategory.CAFE);
        PetSatisfaction satisfaction = PetSatisfaction.builder()
                .pet(pet)
                .facility(likedFacility)
                .score(4.5f)
                .build();

        // 매치 코스: 카테고리(CAFE)가 겹친다. 비매치 코스: 카테고리(RESTAURANT)가 안 겹친다.
        // 최신순으로는 비매치 코스가 먼저지만, 개인화 정렬로는 매치 코스가 먼저 와야 한다.
        Facility matchedStop = facility(1L, "취향 맞는 곳");
        ReflectionTestUtils.setField(matchedStop, "category", FacilityCategory.CAFE);
        Facility unmatchedStop = facility(2L, "취향 안 맞는 곳");
        ReflectionTestUtils.setField(unmatchedStop, "category", FacilityCategory.RESTAURANT);

        Course matchedCourse = publicCourse(owner, 10L, matchedStop);
        Course unmatchedCourse = publicCourse(owner, 11L, unmatchedStop);

        PageRequest pageable = PageRequest.of(0, 15);
        when(petRepository.findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(2L)).thenReturn(List.of(pet));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L))).thenReturn(List.of(satisfaction));
        when(reviewTagRepository.findDistinctTagsByFacilityIdIn(Set.of(100L))).thenReturn(Set.of());
        // 최신순 후보 풀에는 비매치 코스가 먼저 온다고 가정(더 최근 생성) — 개인화 정렬이
        // 이 순서를 뒤집어야 테스트 의미가 있다.
        when(courseRepository.findAllBySourceAndIsPublicTrueOrderByCreatedAtDesc(CourseSource.CUSTOM, PageRequest.of(0, 200)))
                .thenReturn(new PageImpl<>(List.of(unmatchedCourse, matchedCourse), PageRequest.of(0, 200), 2));
        when(reviewTagRepository.findTagsByFacilityIdIn(List.of(2L, 1L))).thenReturn(List.of());
        when(reviewPetRepository.findKindAndBreedSizeByFacilityIdIn(List.of(2L, 1L))).thenReturn(List.of());

        CourseResponseDTO.PublicCourseResult result = courseQueryService.getPublicCourses(2L, pageable);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).courseId()).isEqualTo(10L);
        assertThat(result.items().get(1).courseId()).isEqualTo(11L);
    }

    private User createUser(
            Long userId,
            String nickname
    ) {
        User user = User.builder()
                .email("test@freepets.com")
                .passwordHash("hash")
                .nickname(nickname)
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Course publicCourse(
            User owner,
            Long courseId,
            Facility stop
    ) {
        Course course = Course.builder()
                .user(owner)
                .name("코스 " + courseId)
                .source(CourseSource.CUSTOM)
                .isPublic(true)
                .build();
        course.replaceStops(List.of(stop));
        ReflectionTestUtils.setField(course, "courseId", courseId);
        return course;
    }

    private Pet pet(
            Long petId,
            Kind kind
    ) {
        Pet pet = Pet.builder()
                .name("몽이")
                .kind(kind)
                .species("믹스")
                .build();
        ReflectionTestUtils.setField(pet, "petId", petId);
        return pet;
    }

    private Facility facility(
            Long facilityId,
            String name
    ) {
        Facility facility = Facility.builder()
                .name(name)
                .category(FacilityCategory.CAFE)
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("128.0"))
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

}
