package com.freepets.domain.petsatisfaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petsatisfaction.dto.PetSatisfactionResponseDTO;
import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;
import com.freepets.domain.petsatisfaction.repository.PetSatisfactionRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class PetSatisfactionQueryServiceTest {

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private PetRepository petRepository;

    @Mock
    private PetSatisfactionRepository petSatisfactionRepository;

    @InjectMocks
    private PetSatisfactionQueryService petSatisfactionQueryService;

    private Facility createFacility(Long facilityId) {
        return createFacility(facilityId, "우리동네 카페");
    }

    private Facility createFacility(
            Long facilityId,
            String name
    ) {
        Facility facility = Facility.builder()
                .name(name)
                .category(FacilityCategory.CAFE)
                .address("서울시 강남구")
                .lat(BigDecimal.ONE)
                .lng(BigDecimal.ONE)
                .phone("02-1234-5678")
                .petAllowed(PetAllowed.ALLOWED)
                .maxWeight(BigDecimal.TEN)
                .contentId("12345")
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

    private User createUser(Long userId) {
        User user = User.builder()
                .email("user" + userId + "@test.com")
                .passwordHash("encodedPassword")
                .nickname("몽이아빠")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Pet createPet(
            Long petId,
            User owner,
            String name
    ) {
        Pet pet = Pet.builder()
                .user(owner)
                .name(name)
                .kind(Kind.DOG)
                .species("말티즈")
                .weight(BigDecimal.valueOf(3.4))
                .breedSize(BreedSize.SMALL)
                .build();
        ReflectionTestUtils.setField(pet, "petId", petId);
        return pet;
    }

    @Test
    void getFacilitySatisfactions_존재하지_않는_시설이면_예외를_던진다() {
        when(facilityRepository.existsById(7L)).thenReturn(false);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> petSatisfactionQueryService.getFacilitySatisfactions(1L, 7L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.FACILITY4041);
    }

    @Test
    void getFacilitySatisfactions_기록없는_반려동물은_기록전으로_표시한다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet recordedPet = createPet(1L, user, "몽이");
        Pet unrecordedPet = createPet(2L, user, "고양미");

        PetSatisfaction satisfaction = PetSatisfaction.builder()
                .pet(recordedPet)
                .facility(facility)
                .score(9.8f)
                .build();

        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(petRepository.findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(1L))
                .thenReturn(List.of(recordedPet, unrecordedPet));
        when(petSatisfactionRepository.findAllByFacilityFacilityIdAndPetPetIdIn(7L, List.of(1L, 2L)))
                .thenReturn(List.of(satisfaction));

        PetSatisfactionResponseDTO.FacilitySatisfactionList result =
                petSatisfactionQueryService.getFacilitySatisfactions(1L, 7L);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).petId()).isEqualTo(1L);
        assertThat(result.items().get(0).score()).isEqualTo(9.8f);
        assertThat(result.items().get(0).isRecorded()).isTrue();
        assertThat(result.items().get(1).petId()).isEqualTo(2L);
        assertThat(result.items().get(1).score()).isNull();
        assertThat(result.items().get(1).isRecorded()).isFalse();
    }
}
