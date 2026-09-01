package com.freepets.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
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

        assertThatThrownBy(() -> courseLikedService.getLikedCourse(1L, List.of(5L)))
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

        assertThatThrownBy(() -> courseLikedService.getLikedCourse(1L, List.of(5L)))
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

        CourseResponseDTO.LikedCourseResult result = courseLikedService.getLikedCourse(1L, List.of(5L, 6L));

        assertThat(result.title()).isEqualTo("몽이·보리가 좋아한 곳");
        assertThat(result.stops()).hasSize(2);
        assertThat(result.stops().get(0).facilityId()).isEqualTo(10L);
        assertThat(result.stops().get(0).avgSatisfaction()).isEqualTo(9.8);
        assertThat(result.stops().get(0).reasonPets()).hasSize(1);
        assertThat(result.stops().get(0).reasonPets().get(0).petName()).isEqualTo("몽이");
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
