package com.freepets.domain.petcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petcheck.dto.PetCheckRequestDTO;
import com.freepets.domain.petcheck.dto.PetCheckResponseDTO;
import com.freepets.domain.petcheck.entity.PetCheck;
import com.freepets.domain.petcheck.entity.PetCheckResult;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.petcheck.service.PetCheckJudgeService.GroupVerdict;
import com.freepets.domain.petcheck.service.PetCheckJudgeService.PetVerdict;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class PetCheckCommandServiceTest {

    @Mock
    private PetCheckRepository petCheckRepository;

    @Mock
    private PetRepository petRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PetCheckJudgeService petCheckJudgeService;

    @InjectMocks
    private PetCheckCommandService petCheckCommandService;

    @Test
    void petIds에_중복이_있어도_본인_소유면_중복없이_조회하고_정상_처리된다() {
        User user = user(1L);
        Pet pet = pet(5L, user);
        Facility facility = facility(7L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(pet));
        when(petCheckJudgeService.judgeGroup(anyList(), eq(facility))).thenReturn(
                new GroupVerdict(
                        PetCheckResult.ALLOWED,
                        List.of(new PetVerdict(pet, PetCheckResult.ALLOWED, "모든 조건을 충족해 출입 가능합니다", List.of()))
                )
        );
        when(petCheckRepository.save(any(PetCheck.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PetCheckResponseDTO.CheckResult result = petCheckCommandService.createCheck(1L, request(7L, 5L, 5L));

        assertThat(result.verdicts()).hasSize(1);
        // 중복 제거된 [5]로만 조회했는지 확인 — [5, 5] 그대로 넘겼다면 이 스텁이 안 맞아 실패한다.
        verify(petRepository).findAllByPetIdInAndDeletedAtIsNull(List.of(5L));
    }

    @Test
    void 존재하지_않는_펫이_섞이면_PET4001() {
        User user = user(1L);
        Facility facility = facility(7L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L, 6L))).thenReturn(List.of());

        assertThatThrownBy(() -> petCheckCommandService.createCheck(1L, request(7L, 5L, 6L)))
                .isInstanceOf(GeneralException.class);
    }

    private PetCheckRequestDTO.CreateRequest request(
            Long facilityId,
            Long... petIds
    ) {
        PetCheckRequestDTO.CreateRequest request = new PetCheckRequestDTO.CreateRequest();
        request.setFacilityId(facilityId);
        request.setPetIds(List.of(petIds));
        return request;
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
            User owner
    ) {
        Pet pet = Pet.builder()
                .user(owner)
                .name("몽이")
                .kind(Kind.DOG)
                .species("말티즈")
                .weight(new BigDecimal("3.2"))
                .breedSize(BreedSize.SMALL)
                .isVaccinated(true)
                .build();
        ReflectionTestUtils.setField(pet, "petId", petId);
        return pet;
    }

    private Facility facility(Long facilityId) {
        Facility facility = Facility.builder()
                .name("테스트 시설")
                .category(FacilityCategory.CAFE)
                .petAllowed(PetAllowed.ALLOWED)
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }
}
