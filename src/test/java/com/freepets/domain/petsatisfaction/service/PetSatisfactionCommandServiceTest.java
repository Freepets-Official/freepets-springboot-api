package com.freepets.domain.petsatisfaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
import com.freepets.domain.petsatisfaction.dto.PetSatisfactionRequestDTO;
import com.freepets.domain.petsatisfaction.dto.PetSatisfactionResponseDTO;
import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;
import com.freepets.domain.petsatisfaction.repository.PetSatisfactionRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class PetSatisfactionCommandServiceTest {

    @Mock
    private PetSatisfactionRepository petSatisfactionRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private PetRepository petRepository;

    @InjectMocks
    private PetSatisfactionCommandService petSatisfactionCommandService;

    private Facility createFacility(Long facilityId) {
        Facility facility = Facility.builder()
                .name("우리동네 카페")
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

    private Pet createPet(Long petId, User owner) {
        Pet pet = Pet.builder()
                .user(owner)
                .name("몽이")
                .kind(Kind.DOG)
                .species("말티즈")
                .weight(BigDecimal.valueOf(3.4))
                .breedSize(BreedSize.SMALL)
                .build();
        ReflectionTestUtils.setField(pet, "petId", petId);
        return pet;
    }

    private PetSatisfactionRequestDTO.UpsertRequest createRequest(float score) {
        PetSatisfactionRequestDTO.UpsertRequest request = new PetSatisfactionRequestDTO.UpsertRequest();
        request.setScore(score);
        return request;
    }

    @Test
    void upsertSatisfaction_새로운_기록이면_생성한다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet = createPet(1L, user);

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet));
        when(petSatisfactionRepository.findByPetPetIdAndFacilityFacilityId(1L, 7L)).thenReturn(Optional.empty());
        when(petSatisfactionRepository.save(any(PetSatisfaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PetSatisfactionResponseDTO.UpsertResult result =
                petSatisfactionCommandService.upsertSatisfaction(1L, 7L, 1L, createRequest(9.8f));

        assertThat(result.petId()).isEqualTo(1L);
        assertThat(result.facilityId()).isEqualTo(7L);
        assertThat(result.score()).isEqualTo(9.8f);
    }

    @Test
    void upsertSatisfaction_기존_기록이_있으면_점수를_덮어쓴다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet = createPet(1L, user);
        PetSatisfaction existing = PetSatisfaction.builder()
                .pet(pet)
                .facility(facility)
                .score(3.0f)
                .build();

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet));
        when(petSatisfactionRepository.findByPetPetIdAndFacilityFacilityId(1L, 7L)).thenReturn(Optional.of(existing));
        when(petSatisfactionRepository.save(any(PetSatisfaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PetSatisfactionResponseDTO.UpsertResult result =
                petSatisfactionCommandService.upsertSatisfaction(1L, 7L, 1L, createRequest(7.1f));

        assertThat(result.score()).isEqualTo(7.1f);
        assertThat(existing.getScore()).isEqualTo(7.1f);
    }

    @Test
    void upsertSatisfaction_존재하지_않는_시설이면_예외를_던진다() {
        when(facilityRepository.findById(7L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> petSatisfactionCommandService.upsertSatisfaction(1L, 7L, 1L, createRequest(9.8f))
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.FACILITY4041);
        verify(petSatisfactionRepository, never()).save(any());
    }

    @Test
    void upsertSatisfaction_존재하지_않는_반려동물이면_예외를_던진다() {
        Facility facility = createFacility(7L);

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> petSatisfactionCommandService.upsertSatisfaction(1L, 7L, 1L, createRequest(9.8f))
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.PET4001);
        verify(petSatisfactionRepository, never()).save(any());
    }

    @Test
    void upsertSatisfaction_다른_사용자의_반려동물이면_예외를_던진다() {
        Facility facility = createFacility(7L);
        User stranger = createUser(2L);
        Pet strangerPet = createPet(1L, stranger);

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(strangerPet));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> petSatisfactionCommandService.upsertSatisfaction(1L, 7L, 1L, createRequest(9.8f))
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.PET4002);
        verify(petSatisfactionRepository, never()).save(any());
    }

    @Test
    void upsertSatisfaction_저장중_DB_유니크_제약에_걸리면_충돌_에러를_던진다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet = createPet(1L, user);

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet));
        when(petSatisfactionRepository.findByPetPetIdAndFacilityFacilityId(1L, 7L)).thenReturn(Optional.empty());
        // 슬라이더 조작 등으로 거의 동시에 두 번 제출되면 둘 다 "기존 기록 없음"으로 보고
        // insert를 시도할 수 있는데, DB의 유니크 제약(pet_id, facility_id)이 뒤늦은 쪽을 막아준다.
        when(petSatisfactionRepository.save(any(PetSatisfaction.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> petSatisfactionCommandService.upsertSatisfaction(1L, 7L, 1L, createRequest(9.8f))
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.SATISFACTION4001);
    }

    @Test
    void upsertSatisfaction_점수를_소수점_첫째자리로_반올림해서_저장한다() {
        Facility facility = createFacility(7L);
        User user = createUser(1L);
        Pet pet = createPet(1L, user);

        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(petRepository.findByPetIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pet));
        when(petSatisfactionRepository.findByPetPetIdAndFacilityFacilityId(1L, 7L)).thenReturn(Optional.empty());
        when(petSatisfactionRepository.save(any(PetSatisfaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PetSatisfactionResponseDTO.UpsertResult result =
                petSatisfactionCommandService.upsertSatisfaction(1L, 7L, 1L, createRequest(9.876543f));

        assertThat(result.score()).isEqualTo(9.9f);
    }
}
