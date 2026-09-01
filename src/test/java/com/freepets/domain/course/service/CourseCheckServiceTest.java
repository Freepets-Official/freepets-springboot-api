package com.freepets.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.course.dto.CourseCheckResponseDTO;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
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
import com.freepets.global.util.GeoUtils;

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
    void DENIED_스톱에만_동적으로_찾은_대안이_채워지고_CONDITIONAL은_대안없이_통과된다() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Facility allowedFacility = facility(1L, "산책로");
        Facility conditionalFacility = facility(2L, "카페");
        Facility deniedFacility = facilityWithCoordinate(3L, "호텔카페", FacilityCategory.CAFE, "37.000", "128.000");
        Facility nearAlternative = facilityWithCoordinate(4L, "대형견카페", FacilityCategory.CAFE, "37.001", "128.001");
        Facility farAlternative = facilityWithCoordinate(6L, "먼카페", FacilityCategory.CAFE, "37.100", "128.100");

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

        // 같은 카테고리(CAFE) 후보 둘 중, 판별을 통과한 것만 대안 자격이 있고 그중 더 가까운 곳을 고른다.
        when(facilityRepository.findAllByCategoryAndIsActiveTrueAndPetAllowedNotAndFacilityIdNotIn(
                eq(FacilityCategory.CAFE), eq(PetAllowed.DENIED), eq(Set.of(1L, 2L, 3L))
        )).thenReturn(List.of(farAlternative, nearAlternative));
        when(petCheckJudgeService.judgeGroup(List.of(몽이), nearAlternative))
                .thenReturn(new GroupVerdict(PetCheckResult.ALLOWED, List.of()));
        when(petCheckJudgeService.judgeGroup(List.of(몽이), farAlternative))
                .thenReturn(new GroupVerdict(PetCheckResult.ALLOWED, List.of()));

        CourseCheckResponseDTO.CourseCheckResult result = courseCheckService.checkCourse(1L, List.of(5L), List.of(1L, 2L, 3L));

        assertThat(result.overall()).isEqualTo(PetCheckResult.DENIED);
        assertThat(result.blockedCount()).isEqualTo(1L);
        assertThat(result.stops()).hasSize(3);

        CourseCheckResponseDTO.Stop conditionalStop = result.stops().get(1);
        assertThat(conditionalStop.overall()).isEqualTo(PetCheckResult.CONDITIONAL);
        assertThat(conditionalStop.alternative()).isNull();
        assertThat(conditionalStop.verdicts().get(0).conditions()).containsExactly("리드줄 필수 착용");

        CourseCheckResponseDTO.Stop deniedStop = result.stops().get(2);
        assertThat(deniedStop.overall()).isEqualTo(PetCheckResult.DENIED);
        assertThat(deniedStop.alternative()).isNotNull();
        assertThat(deniedStop.alternative().facilityId()).isEqualTo(4L); // 더 가까운 쪽

        double expectedDistanceKm = Math.round(GeoUtils.distanceMeters(
                deniedFacility.getLat(), deniedFacility.getLng(), nearAlternative.getLat(), nearAlternative.getLng()
        ) / 100.0) / 10.0;
        assertThat(deniedStop.alternative().distanceKm()).isEqualTo(expectedDistanceKm);

        verify(petCheckRepository, times(3)).save(any(PetCheck.class));
    }

    @Test
    void 대안_후보가_전부_판별에서_막히면_대안이_null이다() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Facility deniedFacility = facilityWithCoordinate(3L, "호텔카페", FacilityCategory.CAFE, "37.000", "128.000");
        Facility candidate = facilityWithCoordinate(4L, "대형견카페", FacilityCategory.CAFE, "37.001", "128.001");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(facilityRepository.findAllById(List.of(3L))).thenReturn(List.of(deniedFacility));
        when(petCheckJudgeService.judgeGroup(List.of(몽이), deniedFacility))
                .thenReturn(new GroupVerdict(PetCheckResult.DENIED,
                        List.of(new PetVerdict(몽이, PetCheckResult.DENIED, "체중 초과", List.of()))));
        when(facilityRepository.findAllByCategoryAndIsActiveTrueAndPetAllowedNotAndFacilityIdNotIn(
                eq(FacilityCategory.CAFE), eq(PetAllowed.DENIED), anyCollection()
        )).thenReturn(List.of(candidate));
        when(petCheckJudgeService.judgeGroup(List.of(몽이), candidate))
                .thenReturn(new GroupVerdict(PetCheckResult.DENIED, List.of()));

        CourseCheckResponseDTO.CourseCheckResult result = courseCheckService.checkCourse(1L, List.of(5L), List.of(3L));

        assertThat(result.stops().get(0).alternative()).isNull();
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

    private Facility facilityWithCoordinate(
            Long facilityId,
            String name,
            FacilityCategory category,
            String lat,
            String lng
    ) {
        Facility facility = Facility.builder()
                .name(name)
                .category(category)
                .petAllowed(PetAllowed.ALLOWED)
                .lat(new BigDecimal(lat))
                .lng(new BigDecimal(lng))
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

}
