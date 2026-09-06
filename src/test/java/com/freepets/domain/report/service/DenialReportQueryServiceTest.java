package com.freepets.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.report.dto.DenialReportResponseDTO;
import com.freepets.domain.report.entity.DenialReason;
import com.freepets.domain.report.entity.FacilityReport;
import com.freepets.domain.report.entity.ReportStatus;
import com.freepets.domain.report.entity.ReportType;
import com.freepets.domain.report.repository.FacilityReportRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class DenialReportQueryServiceTest {

    @Mock
    private FacilityReportRepository facilityReportRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private PetCheckRepository petCheckRepository;

    @InjectMocks
    private DenialReportQueryService denialReportQueryService;

    @Test
    void recent은_존재하지_않는_시설이면_예외() {
        when(facilityRepository.existsById(7L)).thenReturn(false);

        assertThatThrownBy(() -> denialReportQueryService.getRecent(7L, 1L))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void recent은_최대_3건으로_페이징해서_조회한다() {
        when(facilityRepository.existsById(7L)).thenReturn(true);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(facilityReportRepository.findAllByFacility_FacilityIdAndIsRealtimeTrueAndUser_IdNotAndCreatedAtAfterOrderByCreatedAtDesc(
                eq(7L), eq(1L), any(), pageableCaptor.capture()
        )).thenReturn(List.of(denialReport(facility(7L), user(2L), DenialReason.INDOOR)));

        List<DenialReportResponseDTO.Report> result = denialReportQueryService.getRecent(7L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isMine()).isFalse();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    }

    @Test
    void mine은_보낸_적_없으면_null을_반환한다() {
        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(facilityReportRepository.findFirstByFacility_FacilityIdAndUser_IdAndIsRealtimeTrueOrderByCreatedAtDesc(7L, 1L))
                .thenReturn(Optional.empty());

        DenialReportResponseDTO.Report result = denialReportQueryService.getMine(7L, 1L);

        assertThat(result).isNull();
    }

    @Test
    void mine은_보낸_적_있으면_mine_true로_반환한다() {
        when(facilityRepository.existsById(7L)).thenReturn(true);
        when(facilityReportRepository.findFirstByFacility_FacilityIdAndUser_IdAndIsRealtimeTrueOrderByCreatedAtDesc(7L, 1L))
                .thenReturn(Optional.of(denialReport(facility(7L), user(1L), DenialReason.WEIGHT)));

        DenialReportResponseDTO.Report result = denialReportQueryService.getMine(7L, 1L);

        assertThat(result).isNotNull();
        assertThat(result.isMine()).isTrue();
    }

    @Test
    void 판별_이력이_없으면_시설_조회_없이_빈_목록을_반환한다() {
        when(petCheckRepository.findDistinctFacilityIdsByUser_Id(eq(1L), any())).thenReturn(List.of());

        List<DenialReportResponseDTO.DenialAlert> result = denialReportQueryService.getMyDenialAlerts(1L);

        assertThat(result).isEmpty();
        verify(facilityReportRepository, org.mockito.Mockito.never())
                .findAllByFacility_FacilityIdInAndIsRealtimeTrueAndUser_IdNotAndCreatedAtAfterOrderByCreatedAtDesc(
                        anyList(), any(), any()
                );
    }

    @Test
    void 시설당_최신_제보_1건만_알림으로_남긴다() {
        Facility facilityA = facility(7L);
        Facility facilityB = facility(8L);
        User other = user(2L);

        FacilityReport older = denialReport(facilityA, other, DenialReason.INDOOR);
        ReflectionTestUtils.setField(older, "createdAt", LocalDateTime.now().minusHours(5));
        FacilityReport newer = denialReport(facilityA, other, DenialReason.WEIGHT);
        ReflectionTestUtils.setField(newer, "createdAt", LocalDateTime.now().minusHours(1));
        FacilityReport onlyOne = denialReport(facilityB, other, DenialReason.CROWDED);
        ReflectionTestUtils.setField(onlyOne, "createdAt", LocalDateTime.now().minusHours(2));

        when(petCheckRepository.findDistinctFacilityIdsByUser_Id(eq(1L), any())).thenReturn(List.of(7L, 8L));
        // 리포지토리는 이미 createdAt desc로 정렬해서 준다고 가정한다(쿼리에 명시돼 있음) — 새것 먼저.
        when(facilityReportRepository.findAllByFacility_FacilityIdInAndIsRealtimeTrueAndUser_IdNotAndCreatedAtAfterOrderByCreatedAtDesc(
                eq(List.of(7L, 8L)), eq(1L), any()
        )).thenReturn(List.of(newer, onlyOne, older));

        List<DenialReportResponseDTO.DenialAlert> result = denialReportQueryService.getMyDenialAlerts(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).facility().facilityId()).isEqualTo(7L);
        assertThat(result.get(0).report().reason()).isEqualTo(DenialReason.WEIGHT); // older가 아니라 newer가 남아야 함
        assertThat(result.get(1).facility().facilityId()).isEqualTo(8L);
    }

    private FacilityReport denialReport(
            Facility facility,
            User user,
            DenialReason reason
    ) {
        return FacilityReport.builder()
                .user(user)
                .facility(facility)
                .reportType(ReportType.DENIED)
                .denialReason(reason)
                .status(ReportStatus.APPLIED)
                .isRealtime(true)
                .build();
    }

    private User user(Long id) {
        User user = User.builder()
                .email("test" + id + "@freepets.com")
                .passwordHash("hash")
                .nickname("테스터" + id)
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Facility facility(Long facilityId) {
        Facility facility = Facility.builder()
                .name("테스트 시설" + facilityId)
                .category(FacilityCategory.CAFE)
                .petAllowed(PetAllowed.ALLOWED)
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }
}
