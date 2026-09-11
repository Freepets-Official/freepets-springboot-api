package com.freepets.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.freepets.domain.report.dto.FacilityConditionInquiryResponseDTO;
import com.freepets.domain.report.entity.FacilityConditionInquiry;
import com.freepets.domain.report.repository.FacilityConditionInquiryRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class FacilityConditionInquiryCommandServiceTest {

    @Mock
    private FacilityConditionInquiryRepository facilityConditionInquiryRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FacilityConditionInquiryCommandService facilityConditionInquiryCommandService;

    @Test
    void 정상_요청되면_저장된다() {
        Facility facility = facility(7L);

        when(userRepository.existsById(1L)).thenReturn(true);
        when(userRepository.getReferenceById(1L)).thenReturn(user(1L));
        when(facilityRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(facility));
        when(facilityConditionInquiryRepository
                .existsByUser_IdAndFacility_FacilityIdAndCreatedAtAfter(eq(1L), eq(7L), any()))
                .thenReturn(false);
        when(facilityConditionInquiryRepository.save(any(FacilityConditionInquiry.class))).thenAnswer(invocation -> {
            FacilityConditionInquiry saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "inquiryId", 100L);
            return saved;
        });

        FacilityConditionInquiryResponseDTO.InquireResult result =
                facilityConditionInquiryCommandService.inquire(1L, 7L, "실내 동반 가능 범위가 궁금해요");

        assertThat(result.inquiryId()).isEqualTo(100L);
        assertThat(result.facilityId()).isEqualTo(7L);
    }

    @Test
    void 메모_없이도_요청할_수_있다() {
        Facility facility = facility(7L);

        when(userRepository.existsById(1L)).thenReturn(true);
        when(userRepository.getReferenceById(1L)).thenReturn(user(1L));
        when(facilityRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(facility));
        when(facilityConditionInquiryRepository
                .existsByUser_IdAndFacility_FacilityIdAndCreatedAtAfter(eq(1L), eq(7L), any()))
                .thenReturn(false);
        when(facilityConditionInquiryRepository.save(any(FacilityConditionInquiry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FacilityConditionInquiryResponseDTO.InquireResult result =
                facilityConditionInquiryCommandService.inquire(1L, 7L, null);

        assertThat(result.facilityId()).isEqualTo(7L);
    }

    @Test
    void 존재하지_않는_유저면_MEMBER4005_시설_조회는_안_한다() {
        when(userRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> facilityConditionInquiryCommandService.inquire(1L, 7L, null))
                .isInstanceOf(GeneralException.class);
        verify(facilityRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void 존재하지_않는_시설이면_FACILITY4001() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(userRepository.getReferenceById(1L)).thenReturn(user(1L));
        when(facilityRepository.findByIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facilityConditionInquiryCommandService.inquire(1L, 7L, null))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 이십사시간_내_이미_요청했으면_REPORT4002_저장은_안_한다() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(userRepository.getReferenceById(1L)).thenReturn(user(1L));
        when(facilityRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(facility(7L)));
        when(facilityConditionInquiryRepository
                .existsByUser_IdAndFacility_FacilityIdAndCreatedAtAfter(eq(1L), eq(7L), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> facilityConditionInquiryCommandService.inquire(1L, 7L, null))
                .isInstanceOf(GeneralException.class);
        verify(facilityConditionInquiryRepository, never()).save(any());
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
