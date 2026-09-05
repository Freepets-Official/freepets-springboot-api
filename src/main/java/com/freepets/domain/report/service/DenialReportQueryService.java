package com.freepets.domain.report.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.report.converter.DenialReportConverter;
import com.freepets.domain.report.dto.DenialReportResponseDTO;
import com.freepets.domain.report.entity.FacilityReport;
import com.freepets.domain.report.repository.FacilityReportRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DenialReportQueryService {

    private static final int MAX_RECENT = 3;

    // 판별 이력이 아주 많은 유저라도 이 화면은 "최근에 가려던 곳" 경고라 오래된 이력까지 다
    // 훑을 필요가 없다 — 무한정 커지는 IN절을 막는 안전판.
    private static final int MAX_CHECKED_FACILITIES = 200;

    private final FacilityReportRepository facilityReportRepository;
    private final FacilityRepository facilityRepository;
    private final PetCheckRepository petCheckRepository;

    // GET .../denial-reports/recent — 타인의 제보, 최신순 최대 3건.
    public List<DenialReportResponseDTO.Report> getRecent(
            Long facilityId,
            Long userId
    ) {
        requireFacility(facilityId);

        LocalDateTime since = LocalDateTime.now().minusDays(FacilityReport.RECENT_WINDOW_DAYS);
        Pageable pageable = PageRequest.of(0, MAX_RECENT);
        List<FacilityReport> reports = facilityReportRepository
                .findAllByFacility_FacilityIdAndIsRealtimeTrueAndUser_IdNotAndCreatedAtAfterOrderByCreatedAtDesc(
                        facilityId,
                        userId,
                        since,
                        pageable
                );

        return DenialReportConverter.toReports(reports, userId);
    }

    // GET .../denial-reports/mine — 만료 없이 내가 보낸 가장 최근 실시간 제보(없으면 null).
    public DenialReportResponseDTO.Report getMine(
            Long facilityId,
            Long userId
    ) {
        requireFacility(facilityId);

        return facilityReportRepository
                .findFirstByFacility_FacilityIdAndUser_IdAndIsRealtimeTrueOrderByCreatedAtDesc(facilityId, userId)
                .map(report -> DenialReportConverter.toReport(report, userId))
                .orElse(null);
    }

    // GET /me/denial-alerts — 내가 판별받은 시설 ∩ 최근 1주 내 타인의 거부 제보가 있는 시설.
    public List<DenialReportResponseDTO.DenialAlert> getMyDenialAlerts(Long userId) {
        List<Long> checkedFacilityIds = petCheckRepository.findDistinctFacilityIdsByUser_Id(
                userId,
                PageRequest.of(0, MAX_CHECKED_FACILITIES)
        );
        if (checkedFacilityIds.isEmpty()) {
            return List.of();
        }

        LocalDateTime since = LocalDateTime.now().minusDays(FacilityReport.RECENT_WINDOW_DAYS);
        List<FacilityReport> reports = facilityReportRepository
                .findAllByFacility_FacilityIdInAndIsRealtimeTrueAndUser_IdNotAndCreatedAtAfterOrderByCreatedAtDesc(
                        checkedFacilityIds,
                        userId,
                        since
                );

        // 시설당 최신 1건만 — createdAt desc로 이미 정렬돼 있어 각 시설을 처음 만난 순간이 최신값이다.
        Map<Long, FacilityReport> latestPerFacility = new LinkedHashMap<>();
        for (FacilityReport report : reports) {
            latestPerFacility.putIfAbsent(report.getFacility().getFacilityId(), report);
        }

        return latestPerFacility.values().stream()
                .map(DenialReportConverter::toDenialAlert)
                .toList();
    }

    private void requireFacility(Long facilityId) {
        if (!facilityRepository.existsById(facilityId)) {
            throw new GeneralException(ErrorStatus.FACILITY4001);
        }
    }
}
