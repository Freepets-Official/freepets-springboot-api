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

    @Test
    void getMySatisfactions_반려동물별로_점수_높은_순_상위_3개_시설만_반환한다() {
        User user = createUser(1L);
        Pet pet = createPet(1L, user, "몽이");
        Facility facility1 = createFacility(1L, "헤이도그 애견호텔&카페");
        Facility facility2 = createFacility(2L, "안목해변 솔숲 산책로");
        Facility facility3 = createFacility(3L, "카페 파도살롱");
        Facility facility4 = createFacility(4L, "네번째로 좋아한 곳");

        List<PetSatisfaction> satisfactions = List.of(
                PetSatisfaction.builder().pet(pet).facility(facility4).score(5.0f).build(),
                PetSatisfaction.builder().pet(pet).facility(facility1).score(9.4f).build(),
                PetSatisfaction.builder().pet(pet).facility(facility3).score(8.1f).build(),
                PetSatisfaction.builder().pet(pet).facility(facility2).score(8.7f).build()
        );

        when(petSatisfactionRepository.findAllByPetUserIdAndPetDeletedAtIsNull(1L)).thenReturn(satisfactions);

        PetSatisfactionResponseDTO.MySatisfactionList result = petSatisfactionQueryService.getMySatisfactions(1L);

        assertThat(result.pets()).hasSize(1);
        PetSatisfactionResponseDTO.PetTopFacilities petResult = result.pets().get(0);
        assertThat(petResult.petId()).isEqualTo(1L);
        assertThat(petResult.petName()).isEqualTo("몽이");
        // 4개 중 상위 3개만, 점수 높은 순으로 남아야 한다 (5.0점짜리 네 번째는 제외).
        assertThat(petResult.topFacilities()).hasSize(3);
        assertThat(petResult.topFacilities()).extracting("score")
                .containsExactly(9.4f, 8.7f, 8.1f);
        assertThat(petResult.topFacilities()).extracting("facilityName")
                .containsExactly("헤이도그 애견호텔&카페", "안목해변 솔숲 산책로", "카페 파도살롱");
    }

    @Test
    void getMySatisfactions_기록이_없는_반려동물은_결과에서_빠진다() {
        when(petSatisfactionRepository.findAllByPetUserIdAndPetDeletedAtIsNull(1L)).thenReturn(List.of());

        PetSatisfactionResponseDTO.MySatisfactionList result = petSatisfactionQueryService.getMySatisfactions(1L);

        assertThat(result.pets()).isEmpty();
    }

    @Test
    void getMySatisfactions_반려동물이_여러_마리면_각각_묶어서_반환한다() {
        User user = createUser(1L);
        Pet dog = createPet(1L, user, "몽이");
        Pet cat = createPet(2L, user, "보리");
        Facility facility = createFacility(7L, "헤이도그 애견호텔&카페");

        List<PetSatisfaction> satisfactions = List.of(
                PetSatisfaction.builder().pet(dog).facility(facility).score(9.4f).build(),
                PetSatisfaction.builder().pet(cat).facility(facility).score(6.2f).build()
        );

        when(petSatisfactionRepository.findAllByPetUserIdAndPetDeletedAtIsNull(1L)).thenReturn(satisfactions);

        PetSatisfactionResponseDTO.MySatisfactionList result = petSatisfactionQueryService.getMySatisfactions(1L);

        assertThat(result.pets()).hasSize(2);
        assertThat(result.pets().get(0).petId()).isEqualTo(1L);
        assertThat(result.pets().get(1).petId()).isEqualTo(2L);
    }
}
