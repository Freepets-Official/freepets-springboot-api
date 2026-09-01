package com.freepets.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.course.dto.CourseCheckResponseDTO;
import com.freepets.domain.facility.entity.AlternativeFacility;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.AlternativeFacilityRepository;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petcheck.entity.PetCheck;
import com.freepets.domain.petcheck.entity.PetCheckResult;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.petcheck.service.PetCheckJudgeService;
import com.freepets.domain.petcheck.service.PetCheckJudgeService.GroupVerdict;
import com.freepets.domain.petcheck.service.PetCheckJudgeService.PetVerdict;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class CourseCheckServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PetRepository petRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private PetCheckRepository petCheckRepository;

    @Mock
    private AlternativeFacilityRepository alternativeFacilityRepository;

    @Mock
    private PetCheckJudgeService petCheckJudgeService;

    @InjectMocks
    private CourseCheckService courseCheckService;

    @Test
    void 존재하지_않는_시설이_있으면_FACILITY4001() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(facilityRepository.findAllById(List.of(3L, 999L))).thenReturn(List.of(facility(3L, "A")));

        assertThatThrownBy(() -> courseCheckService.checkCourse(1L, List.of(5L), List.of(3L, 999L)))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void DENIED_스톱에만_대안이_채워지고_CONDITIONAL은_대안없이_통과된다() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Facility allowedFacility = facility(1L, "산책로");
        Facility conditionalFacility = facility(2L, "카페");
        Facility deniedFacility = facility(3L, "호텔카페");
        Facility alternative = facility(4L, "대형견카페");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(facilityRepository.findAllById(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(allowedFacility, conditionalFacility, deniedFacility));

        when(petCheckJudgeService.judgeGroup(List.of(몽이), allowedFacility))
                .thenReturn(new GroupVerdict(PetCheckResult.ALLOWED,
                        List.of(new PetVerdict(몽이, PetCheckResult.ALLOWED, "모든 조건을 충족해 출입 가능합니다", List.of()))));
        when(petCheckJudgeService.judgeGroup(List.of(몽이), conditionalFacility))
                .thenReturn(new GroupVerdict(PetCheckResult.CONDITIONAL,
                        List.of(new PetVerdict(몽이, PetCheckResult.CONDITIONAL, "출입은 가능하지만 아래 조건을 확인해 주세요", List.of("리드줄 필수 착용")))));
        when(petCheckJudgeService.judgeGroup(List.of(몽이), deniedFacility))
                .thenReturn(new GroupVerdict(PetCheckResult.DENIED,
                        List.of(new PetVerdict(몽이, PetCheckResult.DENIED, "체중 초과", List.of()))));

        AlternativeFacility alt = AlternativeFacility.builder()
                .facility(deniedFacility)
                .alternativeFacility(alternative)
                .distanceKm(new BigDecimal("1.20"))
                .build();
        when(alternativeFacilityRepository.findAllByFacilityFacilityIdOrderByDistanceKmAsc(3L))
                .thenReturn(List.of(alt));

        CourseCheckResponseDTO.CourseCheckResult result = courseCheckService.checkCourse(1L, List.of(5L), List.of(1L, 2L, 3L));

        assertThat(result.overall()).isEqualTo(PetCheckResult.DENIED);
        assertThat(result.stops()).hasSize(3);

        CourseCheckResponseDTO.Stop conditionalStop = result.stops().get(1);
        assertThat(conditionalStop.overall()).isEqualTo(PetCheckResult.CONDITIONAL);
        assertThat(conditionalStop.alternatives()).isEmpty();
        assertThat(conditionalStop.verdicts().get(0).conditions()).containsExactly("리드줄 필수 착용");

        CourseCheckResponseDTO.Stop deniedStop = result.stops().get(2);
        assertThat(deniedStop.overall()).isEqualTo(PetCheckResult.DENIED);
        assertThat(deniedStop.alternatives()).hasSize(1);
        assertThat(deniedStop.alternatives().get(0).facilityId()).isEqualTo(4L);
        assertThat(deniedStop.alternatives().get(0).distanceKm()).isEqualTo(1.20);

        verify(petCheckRepository, times(3)).save(any(PetCheck.class));
    }

    @Test
    void 스톱_시각이_10시부터_90분_간격으로_매겨진다() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Facility a = facility(1L, "A");
        Facility b = facility(2L, "B");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(facilityRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(a, b));
        when(petCheckJudgeService.judgeGroup(any(), any())).thenReturn(
                new GroupVerdict(PetCheckResult.ALLOWED,
                        List.of(new PetVerdict(몽이, PetCheckResult.ALLOWED, "모든 조건을 충족해 출입 가능합니다", List.of())))
        );

        CourseCheckResponseDTO.CourseCheckResult result = courseCheckService.checkCourse(1L, List.of(5L), List.of(1L, 2L));

        assertThat(result.stops().get(0).time()).isEqualTo("10:00");
        assertThat(result.stops().get(1).time()).isEqualTo("11:30");
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
                .breedSize(BreedSize.SMALL)
                .isVaccinated(true)
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
                .petAllowed(PetAllowed.ALLOWED)
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

}
