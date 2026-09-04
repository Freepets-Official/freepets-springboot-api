package com.freepets.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.CourseTheme;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;
import com.freepets.domain.petsatisfaction.repository.FacilityAverageSatisfaction;
import com.freepets.domain.petsatisfaction.repository.PetSatisfactionRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class CourseLikedServiceTest {

    @Mock
    private PetRepository petRepository;

    @Mock
    private PetSatisfactionRepository petSatisfactionRepository;

    @Mock
    private FacilityRepository facilityRepository;

    // 카테고리 다양성/거리순 조립은 CourseAssemblyServiceTest가 따로 검증하므로, 여기서는
    // 실제 인스턴스를 @Spy로 넣어서(모킹 X) CourseLikedService 자체의 후보 산출·필터링 로직만 본다.
    @Spy
    private CourseAssemblyService courseAssemblyService = new CourseAssemblyService();

    @InjectMocks
    private CourseLikedService courseLikedService;

    @Test
    void 방문_기록이_전혀_없으면_COURSE4002() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L))).thenReturn(List.of());

        assertThatThrownBy(() -> courseLikedService.getLikedCourse(1L, List.of(5L), null, null, null, null))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 평균_6점5_미만인_시설은_후보에서_빠지고_최종_후보가_2곳_미만이면_COURSE4002() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Facility facilityA = facility(10L, "A", FacilityCategory.CAFE);
        PetSatisfaction satisfaction = PetSatisfaction.builder().pet(몽이).facility(facilityA).score(9.0f).build();

        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L))).thenReturn(List.of(satisfaction));
        when(petSatisfactionRepository.findAverageScoreByFacilityIdIn(Set.of(10L)))
                .thenReturn(List.of(averageOf(10L, 6.0))); // 6.5 미만

        assertThatThrownBy(() -> courseLikedService.getLikedCourse(1L, List.of(5L), null, null, null, null))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 정상_케이스_avgSatisfaction과_reasonPets가_채워진다() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Pet 보리 = pet(6L, user, "보리");
        Facility facilityA = facility(10L, "카페A", FacilityCategory.CAFE);
        Facility facilityB = facility(11L, "관광지A", FacilityCategory.TOUR);

        PetSatisfaction 몽이A = PetSatisfaction.builder().pet(몽이).facility(facilityA).score(9.8f).build();
        PetSatisfaction 보리B = PetSatisfaction.builder().pet(보리).facility(facilityB).score(8.0f).build();

        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L, 6L))).thenReturn(List.of(몽이, 보리));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L, 6L)))
                .thenReturn(List.of(몽이A, 보리B));
        when(petSatisfactionRepository.findAverageScoreByFacilityIdIn(anyCollection()))
                .thenReturn(List.of(averageOf(10L, 9.8), averageOf(11L, 8.0)));
        when(facilityRepository.findAllById(anyCollection())).thenReturn(List.of(facilityA, facilityB));

        CourseResponseDTO.LikedCourseResult result = courseLikedService.getLikedCourse(1L, List.of(5L, 6L), null, null, null, null);

        assertThat(result.title()).isEqualTo("몽이·보리가 좋아한 곳");
        assertThat(result.stops()).hasSize(2);
        assertThat(result.stops().get(0).facilityId()).isEqualTo(10L);
        assertThat(result.stops().get(0).avgSatisfaction()).isEqualTo(9.8);
        assertThat(result.stops().get(0).reasonPets()).hasSize(1);
        assertThat(result.stops().get(0).reasonPets().get(0).petName()).isEqualTo("몽이");
    }

    @Test
    void 지역_필터를_지정하면_다른_지역_후보는_제외된다() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Facility facilityA = facility(10L, "카페A", FacilityCategory.CAFE);
        Facility facilityB = facility(11L, "관광지A", FacilityCategory.TOUR);
        Facility facilityC = facility(12L, "다른지역카페", FacilityCategory.RESTAURANT);
        ReflectionTestUtils.setField(facilityA, "sido", "강원특별자치도");
        ReflectionTestUtils.setField(facilityB, "sido", "강원특별자치도");
        ReflectionTestUtils.setField(facilityC, "sido", "서울특별시");

        PetSatisfaction satisfactionA = PetSatisfaction.builder().pet(몽이).facility(facilityA).score(9.8f).build();
        PetSatisfaction satisfactionB = PetSatisfaction.builder().pet(몽이).facility(facilityB).score(9.0f).build();
        PetSatisfaction satisfactionC = PetSatisfaction.builder().pet(몽이).facility(facilityC).score(8.5f).build();

        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L)))
                .thenReturn(List.of(satisfactionA, satisfactionB, satisfactionC));
        when(petSatisfactionRepository.findAverageScoreByFacilityIdIn(anyCollection()))
                .thenReturn(List.of(averageOf(10L, 9.8), averageOf(11L, 9.0), averageOf(12L, 8.5)));
        when(facilityRepository.findAllById(anyCollection())).thenReturn(List.of(facilityA, facilityB, facilityC));

        CourseResponseDTO.LikedCourseResult result = courseLikedService
                .getLikedCourse(1L, List.of(5L), null, "강원특별자치도", null, null);

        assertThat(result.stops()).extracting(CourseResponseDTO.LikedStop::facilityId)
                .containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void sido_없이_sigungu만_주면_무시된다() {
        // "고성군"처럼 서로 다른 시/도(강원특별자치도·경상남도)에 같은 이름의 시/군/구가 실제로
        // 있어서, sido 없이 sigungu만으로 걸러내면 엉뚱한 지역의 동명 시/군/구까지 섞일 수 있었다
        // — sido 없이 sigungu만 오면 지역 필터 자체를 무시해야 한다(컨트롤러 문서와 일치).
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        // 카테고리를 다르게 둔다 — assemble()의 "카테고리당 1곳" 규칙 때문에 둘 다 같은
        // 카테고리면 지역 필터와 무관하게 조립 단계에서 하나만 남아 이 테스트의 관심사(지역
        // 필터 자체가 무시되는지)를 검증할 수 없다.
        Facility gangwonGoseong = facility(10L, "강원고성카페", FacilityCategory.CAFE);
        Facility gyeongnamGoseong = facility(11L, "경남고성관광지", FacilityCategory.TOUR);
        ReflectionTestUtils.setField(gangwonGoseong, "sido", "강원특별자치도");
        ReflectionTestUtils.setField(gangwonGoseong, "sigungu", "고성군");
        ReflectionTestUtils.setField(gyeongnamGoseong, "sido", "경상남도");
        ReflectionTestUtils.setField(gyeongnamGoseong, "sigungu", "고성군");

        PetSatisfaction satisfactionA = PetSatisfaction.builder().pet(몽이).facility(gangwonGoseong).score(9.8f).build();
        PetSatisfaction satisfactionB = PetSatisfaction.builder().pet(몽이).facility(gyeongnamGoseong).score(9.0f).build();

        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L)))
                .thenReturn(List.of(satisfactionA, satisfactionB));
        when(petSatisfactionRepository.findAverageScoreByFacilityIdIn(anyCollection()))
                .thenReturn(List.of(averageOf(10L, 9.8), averageOf(11L, 9.0)));
        when(facilityRepository.findAllById(anyCollection())).thenReturn(List.of(gangwonGoseong, gyeongnamGoseong));

        // sido 없이 sigungu="고성군"만 넘긴다 — 둘 다 남아야 한다(어느 도의 고성군인지 알 수 없어
        // sigungu만으로는 못 좁힌다).
        CourseResponseDTO.LikedCourseResult result = courseLikedService
                .getLikedCourse(1L, List.of(5L), null, null, "고성군", null);

        assertThat(result.stops()).extracting(CourseResponseDTO.LikedStop::facilityId)
                .containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void 테마를_여러_개_지정하면_그중_하나라도_맞으면_통과한다() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Facility facilityA = facility(10L, "카페A", FacilityCategory.CAFE); // PET_CAFE 매치
        Facility facilityB = facility(11L, "관광지A", FacilityCategory.TOUR); // SEASIDE_WALK 매치
        Facility facilityC = facility(12L, "식당A", FacilityCategory.RESTAURANT); // 둘 다 불일치
        ReflectionTestUtils.setField(facilityA, "smallCategoryCode", "FD050100"); // 카페
        ReflectionTestUtils.setField(facilityB, "smallCategoryCode", "NA020900"); // 해변. 해수욕장

        PetSatisfaction satisfactionA = PetSatisfaction.builder().pet(몽이).facility(facilityA).score(9.8f).build();
        PetSatisfaction satisfactionB = PetSatisfaction.builder().pet(몽이).facility(facilityB).score(9.0f).build();
        PetSatisfaction satisfactionC = PetSatisfaction.builder().pet(몽이).facility(facilityC).score(8.5f).build();

        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L)))
                .thenReturn(List.of(satisfactionA, satisfactionB, satisfactionC));
        when(petSatisfactionRepository.findAverageScoreByFacilityIdIn(anyCollection()))
                .thenReturn(List.of(averageOf(10L, 9.8), averageOf(11L, 9.0), averageOf(12L, 8.5)));
        when(facilityRepository.findAllById(anyCollection())).thenReturn(List.of(facilityA, facilityB, facilityC));

        CourseResponseDTO.LikedCourseResult result = courseLikedService.getLikedCourse(
                1L, List.of(5L), null, null, null, Set.of(CourseTheme.PET_CAFE, CourseTheme.SEASIDE_WALK)
        );

        assertThat(result.stops()).extracting(CourseResponseDTO.LikedStop::facilityId)
                .containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void 지역_필터가_있으면_근처_식당_후보를_식사_스톱으로_추가한다() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Facility facilityA = facility(10L, "카페A", FacilityCategory.CAFE);
        Facility facilityB = facility(11L, "관광지A", FacilityCategory.TOUR);
        Facility meal = facility(20L, "식당A", FacilityCategory.RESTAURANT);
        ReflectionTestUtils.setField(facilityA, "sido", "강원특별자치도");
        ReflectionTestUtils.setField(facilityB, "sido", "강원특별자치도");

        PetSatisfaction satisfactionA = PetSatisfaction.builder().pet(몽이).facility(facilityA).score(9.8f).build();
        PetSatisfaction satisfactionB = PetSatisfaction.builder().pet(몽이).facility(facilityB).score(9.0f).build();

        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L)))
                .thenReturn(List.of(satisfactionA, satisfactionB));
        when(petSatisfactionRepository.findAverageScoreByFacilityIdIn(anyCollection()))
                .thenReturn(List.of(averageOf(10L, 9.8), averageOf(11L, 9.0)));
        when(facilityRepository.findAllById(anyCollection())).thenReturn(List.of(facilityA, facilityB));
        when(facilityRepository.findPresetCandidates("강원특별자치도", null, Set.of(FacilityCategory.RESTAURANT)))
                .thenReturn(List.of(meal));

        CourseResponseDTO.LikedCourseResult result = courseLikedService
                .getLikedCourse(1L, List.of(5L), null, "강원특별자치도", null, null);

        assertThat(result.stops()).hasSize(3);
        CourseResponseDTO.LikedStop mealStop = result.stops().stream()
                .filter(CourseResponseDTO.LikedStop::isMealStop)
                .findFirst().orElseThrow();
        assertThat(mealStop.facilityId()).isEqualTo(20L);
        // 식사 스톱은 이 아이가 실제로 방문·평가한 곳이 아니므로 avgSatisfaction/reasonPets가 비어야 한다.
        assertThat(mealStop.avgSatisfaction()).isEqualTo(0.0);
        assertThat(mealStop.reasonPets()).isEmpty();
    }

    @Test
    void 지역_필터가_없어도_좋아한_시설_좌표_기준으로_식사_스톱을_찾는다() {
        // sido를 안 넘겨도, 이 아이가 좋아한 시설 중 좌표 있는 곳(likedAnchor)이 있으면 그
        // 주변을 경계 사각형으로 좁혀 식사 스톱 후보를 찾아야 한다(findPresetCandidates 대신
        // findByCategoryWithinBoundingBox).
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Facility facilityA = facility(10L, "카페A", FacilityCategory.CAFE);
        Facility facilityB = facility(11L, "관광지A", FacilityCategory.TOUR);
        Facility meal = facility(20L, "식당A", FacilityCategory.RESTAURANT);

        PetSatisfaction satisfactionA = PetSatisfaction.builder().pet(몽이).facility(facilityA).score(9.8f).build();
        PetSatisfaction satisfactionB = PetSatisfaction.builder().pet(몽이).facility(facilityB).score(9.0f).build();

        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L)))
                .thenReturn(List.of(satisfactionA, satisfactionB));
        when(petSatisfactionRepository.findAverageScoreByFacilityIdIn(anyCollection()))
                .thenReturn(List.of(averageOf(10L, 9.8), averageOf(11L, 9.0)));
        when(facilityRepository.findAllById(anyCollection())).thenReturn(List.of(facilityA, facilityB));
        when(facilityRepository.findByCategoryWithinBoundingBox(
                eq(FacilityCategory.RESTAURANT), any(), any(), any(), any()
        )).thenReturn(List.of(meal));

        CourseResponseDTO.LikedCourseResult result = courseLikedService
                .getLikedCourse(1L, List.of(5L), null, null, null, null);

        assertThat(result.stops()).hasSize(3);
        CourseResponseDTO.LikedStop mealStop = result.stops().stream()
                .filter(CourseResponseDTO.LikedStop::isMealStop)
                .findFirst().orElseThrow();
        assertThat(mealStop.facilityId()).isEqualTo(20L);
    }

    @Test
    void 지역_필터로_최종_후보가_2곳_미만이면_COURSE4002() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Facility facilityA = facility(10L, "카페A", FacilityCategory.CAFE);
        Facility facilityB = facility(11L, "다른지역카페", FacilityCategory.TOUR);
        ReflectionTestUtils.setField(facilityA, "sido", "강원특별자치도");
        ReflectionTestUtils.setField(facilityB, "sido", "서울특별시");

        PetSatisfaction satisfactionA = PetSatisfaction.builder().pet(몽이).facility(facilityA).score(9.8f).build();
        PetSatisfaction satisfactionB = PetSatisfaction.builder().pet(몽이).facility(facilityB).score(9.0f).build();

        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L)))
                .thenReturn(List.of(satisfactionA, satisfactionB));
        when(petSatisfactionRepository.findAverageScoreByFacilityIdIn(anyCollection()))
                .thenReturn(List.of(averageOf(10L, 9.8), averageOf(11L, 9.0)));
        when(facilityRepository.findAllById(anyCollection())).thenReturn(List.of(facilityA, facilityB));

        assertThatThrownBy(() -> courseLikedService.getLikedCourse(1L, List.of(5L), null, "강원특별자치도", null, null))
                .isInstanceOf(GeneralException.class);
    }

    private FacilityAverageSatisfaction averageOf(
            Long facilityId,
            double avgScore
    ) {
        return new FacilityAverageSatisfaction() {
            @Override
            public Long getFacilityId() {
                return facilityId;
            }

            @Override
            public Double getAvgScore() {
                return avgScore;
            }
        };
    }

    private User user(Long id) {
        User user = User.builder()
                .email("test@freepets.com")
                .passwordHash("hash")
                .nickname("테스터")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Pet pet(
            Long petId,
            User owner,
            String name
    ) {
        Pet pet = Pet.builder()
                .user(owner)
                .name(name)
                .kind(Kind.DOG)
                .species("말티즈")
                .weight(new BigDecimal("3.2"))
                .isVaccinated(true)
                .build();
        ReflectionTestUtils.setField(pet, "petId", petId);
        return pet;
    }

    private Facility facility(
            Long facilityId,
            String name,
            FacilityCategory category
    ) {
        Facility facility = Facility.builder()
                .name(name)
                .category(category)
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("128.0"))
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

}
