package com.freepets.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.freepets.domain.report.dto.DenialReportResponseDTO;
import com.freepets.domain.report.entity.DenialReason;
import com.freepets.domain.report.entity.FacilityReport;
import com.freepets.domain.report.entity.ReportStatus;
import com.freepets.domain.report.repository.FacilityReportRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class DenialReportCommandServiceTest {

    @Mock
    private FacilityReportRepository facilityReportRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DenialReportCommandService denialReportCommandService;

    @Test
    void 정상_접수되면_실시간_거부_제보가_저장된다() {
        User user = user(1L);
        Facility facility = facility(7L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility));
        when(facilityReportRepository.existsByUser_IdAndFacility_FacilityIdAndIsRealtimeTrueAndCreatedAtAfter(eq(1L), eq(7L), any()))
                .thenReturn(false);
        when(facilityReportRepository.save(any(FacilityReport.class))).thenAnswer(invocation -> {
            FacilityReport saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "reportId", 100L);
            return saved;
        });
        when(facilityReportRepository.countByFacility_FacilityIdAndIsRealtimeTrueAndCreatedAtAfter(eq(7L), any()))
                .thenReturn(1L);

        DenialReportResponseDTO.Report result = denialReportCommandService.report(1L, 7L, DenialReason.WEIGHT);

        assertThat(result.reportId()).isEqualTo(100L);
        assertThat(result.facilityId()).isEqualTo(7L);
        assertThat(result.reason()).isEqualTo(DenialReason.WEIGHT);
        assertThat(result.content()).isEqualTo("현장 거부 · 체중 초과");
        assertThat(result.isMine()).isTrue();
        assertThat(result.isRealtime()).isTrue();
        assertThat(result.status()).isEqualTo(ReportStatus.APPLIED);
    }

    @Test
    void 존재하지_않는_유저면_MEMBER4005_시설_조회는_안_한다() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> denialReportCommandService.report(1L, 7L, DenialReason.WEIGHT))
                .isInstanceOf(GeneralException.class);
        verifyNoInteractions(facilityRepository);
    }

    @Test
    void 존재하지_않는_시설이면_FACILITY4001() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(facilityRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> denialReportCommandService.report(1L, 7L, DenialReason.WEIGHT))
                .isInstanceOf(GeneralException.class);
        verifyNoInteractions(facilityReportRepository);
    }

    @Test
    void 이십사시간_내_이미_제보했으면_REPORT4001_저장은_안_한다() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility(7L)));
        when(facilityReportRepository.existsByUser_IdAndFacility_FacilityIdAndIsRealtimeTrueAndCreatedAtAfter(eq(1L), eq(7L), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> denialReportCommandService.report(1L, 7L, DenialReason.WEIGHT))
                .isInstanceOf(GeneralException.class);
        verify(facilityReportRepository, never()).save(any());
    }

    @Test
    void 최근_제보가_임계치_이상이어도_저장_자체는_예외_없이_성공한다() {
        // 관리자 승격 API가 없어 로그만 남기는 지점 — 예외를 던지면 안 된다.
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(facilityRepository.findById(7L)).thenReturn(Optional.of(facility(7L)));
        when(facilityReportRepository.existsByUser_IdAndFacility_FacilityIdAndIsRealtimeTrueAndCreatedAtAfter(eq(1L), eq(7L), any()))
                .thenReturn(false);
        when(facilityReportRepository.save(any(FacilityReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(facilityReportRepository.countByFacility_FacilityIdAndIsRealtimeTrueAndCreatedAtAfter(eq(7L), any()))
                .thenReturn(5L);

        assertThatCode(() -> denialReportCommandService.report(1L, 7L, DenialReason.CROWDED))
                .doesNotThrowAnyException();
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
