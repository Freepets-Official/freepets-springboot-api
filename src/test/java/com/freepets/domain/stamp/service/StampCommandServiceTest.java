package com.freepets.domain.stamp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.stamp.dto.StampRequestDTO;
import com.freepets.domain.stamp.dto.StampResponseDTO;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.infra.s3.S3ImageService;

/**
 * StampCommandService는 이제 조율만 한다(DB 쓰기는 StampPersistenceService로 옮김) — 여기서는
 * 시설/반려동물 검증, isVerifiedOnSite 판정, 사진 업로드, StampPersistenceService로의 위임만
 * 확인한다. 도장 저장·승격·지역 완성 시나리오는 StampPersistenceServiceTest 참고.
 */
@ExtendWith(MockitoExtension.class)
class StampCommandServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private PetRepository petRepository;

    @Mock
    private S3ImageService s3ImageService;

    @Mock
    private StampPersistenceService stampPersistenceService;

    private StampCommandService stampCommandService;

    private void setUpService() {
        stampCommandService = new StampCommandService(
                userRepository, facilityRepository, petRepository, s3ImageService, stampPersistenceService
        );
    }

    private User user() {
        User user = User.builder()
                .email("test@freepets.com")
                .passwordHash("hash")
                .nickname("테스터")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private Facility facility(long facilityId) {
        Facility facility = Facility.builder()
                .name("헤이도그 애견카페")
                .sido("강원특별자치도")
                .sigungu("강릉시")
                .sidoCode("51")
                .sigunguCode("150")
                .lat(new BigDecimal("37.751700"))
                .lng(new BigDecimal("128.876000"))
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

    private StampRequestDTO.CreateRequest request(Long facilityId) {
        StampRequestDTO.CreateRequest request = new StampRequestDTO.CreateRequest();
        request.setFacilityId(facilityId);
        return request;
    }

    private StampResponseDTO.StampResult dummyResult() {
        return new StampResponseDTO.StampResult(
                1L, 7L, "헤이도그 애견카페", "강원특별자치도", "강릉시", "51", "150",
                List.of(), null, true, LocalDateTime.now(), true, false, null, null
        );
    }

    @Test
    void 존재하지_않는_시설이면_예외가_발생한다() {
        setUpService();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(facilityRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stampCommandService.createStamp(1L, request(999L)))
                .isInstanceOf(GeneralException.class);
        verify(stampPersistenceService, never()).persist(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void 본인_소유가_아닌_반려동물이_섞이면_예외가_발생한다() {
        setUpService();
        User owner = user();
        User otherUser = User.builder()
                .email("other@freepets.com").passwordHash("hash").nickname("다른유저").provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(otherUser, "id", 2L);
        Pet othersPet = Pet.builder().user(otherUser).name("몽이").build();
        ReflectionTestUtils.setField(othersPet, "petId", 99L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility(7L)));
        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(99L))).thenReturn(List.of(othersPet));

        StampRequestDTO.CreateRequest request = request(7L);
        request.setPetIds(List.of(99L));

        assertThatThrownBy(() -> stampCommandService.createStamp(1L, request))
                .isInstanceOf(GeneralException.class);
        verify(stampPersistenceService, never()).persist(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void 좌표가_반경_안쪽이면_isVerifiedOnSite_true로_위임한다() {
        setUpService();
        User user = user();
        Facility facility = facility(7L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(stampPersistenceService.persist(any(), any(), any(), any(), anyBoolean(), any())).thenReturn(dummyResult());

        StampRequestDTO.CreateRequest request = request(7L);
        // facility 좌표(37.7517, 128.876)와 거의 같은 좌표 — 반경(200m) 이내다.
        request.setLat(37.7517);
        request.setLng(128.876);

        stampCommandService.createStamp(1L, request);

        ArgumentCaptor<Boolean> verifiedCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(stampPersistenceService).persist(eq(user), eq(facility), eq(List.of()), isNull(), verifiedCaptor.capture(), any());
        assertThat(verifiedCaptor.getValue()).isTrue();
    }

    @Test
    void 좌표가_반경_밖이면_isVerifiedOnSite_false로_위임한다() {
        setUpService();
        User user = user();
        Facility facility = facility(7L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(stampPersistenceService.persist(any(), any(), any(), any(), anyBoolean(), any())).thenReturn(dummyResult());

        StampRequestDTO.CreateRequest request = request(7L);
        // 서울시청 좌표 — facility(강릉)과 200km 이상 떨어져 있다.
        request.setLat(37.5665);
        request.setLng(126.9780);

        stampCommandService.createStamp(1L, request);

        ArgumentCaptor<Boolean> verifiedCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(stampPersistenceService).persist(eq(user), eq(facility), eq(List.of()), isNull(), verifiedCaptor.capture(), any());
        assertThat(verifiedCaptor.getValue()).isFalse();
    }

    @Test
    void 좌표가_없으면_isVerifiedOnSite_false로_위임한다() {
        setUpService();
        User user = user();
        Facility facility = facility(7L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(stampPersistenceService.persist(any(), any(), any(), any(), anyBoolean(), any())).thenReturn(dummyResult());

        stampCommandService.createStamp(1L, request(7L));

        ArgumentCaptor<Boolean> verifiedCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(stampPersistenceService).persist(eq(user), eq(facility), eq(List.of()), isNull(), verifiedCaptor.capture(), any());
        assertThat(verifiedCaptor.getValue()).isFalse();
    }

    @Test
    void 마이그레이션_흐름은_좌표_없이_verifiedOnSite와_createdAt을_그대로_위임한다() {
        setUpService();
        User user = user();
        Facility facility = facility(7L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(stampPersistenceService.persist(any(), any(), any(), any(), anyBoolean(), any())).thenReturn(dummyResult());

        StampRequestDTO.CreateRequest request = request(7L);
        request.setVerifiedOnSite(true);
        LocalDateTime pastMoment = LocalDateTime.now().minusMonths(3);
        request.setCreatedAt(pastMoment);

        stampCommandService.createStamp(1L, request);

        verify(stampPersistenceService).persist(eq(user), eq(facility), eq(List.of()), isNull(), eq(true), eq(pastMoment));
    }

    @Test
    void 사진이_있으면_S3에_업로드하고_그_URL로_위임한다() {
        setUpService();
        User user = user();
        Facility facility = facility(7L);
        MockMultipartFile photo = new MockMultipartFile("photo", "cat.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(s3ImageService.upload(photo)).thenReturn("https://freepets-bucket.s3.amazonaws.com/photo.jpg");
        when(stampPersistenceService.persist(any(), any(), any(), any(), anyBoolean(), any())).thenReturn(dummyResult());

        StampRequestDTO.CreateRequest request = request(7L);
        request.setPhoto(photo);

        stampCommandService.createStamp(1L, request);

        verify(stampPersistenceService).persist(
                eq(user), eq(facility), eq(List.of()), eq("https://freepets-bucket.s3.amazonaws.com/photo.jpg"), anyBoolean(), any()
        );
    }

    @Test
    void 사진이_없으면_업로드하지_않고_photoUrl_null로_위임한다() {
        setUpService();
        User user = user();
        Facility facility = facility(7L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(stampPersistenceService.persist(any(), any(), any(), any(), anyBoolean(), any())).thenReturn(dummyResult());

        stampCommandService.createStamp(1L, request(7L));

        verify(s3ImageService, never()).upload(any());
        verify(stampPersistenceService).persist(eq(user), eq(facility), eq(List.of()), isNull(), anyBoolean(), any());
    }

    @Test
    void 결과는_persist의_반환값을_그대로_돌려준다() {
        setUpService();
        User user = user();
        Facility facility = facility(7L);
        StampResponseDTO.StampResult expected = dummyResult();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(stampPersistenceService.persist(any(), any(), any(), any(), anyBoolean(), any())).thenReturn(expected);

        StampResponseDTO.StampResult actual = stampCommandService.createStamp(1L, request(7L));

        assertThat(actual).isSameAs(expected);
    }

}
