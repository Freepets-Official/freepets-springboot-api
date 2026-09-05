package com.freepets.domain.report.converter;

import java.util.List;

import com.freepets.domain.report.dto.DenialReportResponseDTO;
import com.freepets.domain.report.entity.FacilityReport;

public class DenialReportConverter {

    // 사진 없는 실시간 제보에 고정으로 주는 가중치. 프론트 app-store.tsx의 기존 정책과 맞춘다 —
    // "현장에서 바로 보낸 제보는 시점이 붙어 있어 사후 기억보다 정확하다"는 게 근거다.
    private static final int REALTIME_WEIGHT = 2;

    private DenialReportConverter() {}

    public static DenialReportResponseDTO.Report toReport(
            FacilityReport report,
            Long viewerUserId
    ) {
        return new DenialReportResponseDTO.Report(
                report.getReportId(),
                report.getFacility().getFacilityId(),
                report.getReportType(),
                "현장 거부 · " + report.getDenialReason().getLabel(),
                report.getDenialReason(),
                REALTIME_WEIGHT,
                false,
                report.getUser().getId().equals(viewerUserId),
                report.isRealtime(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }

    public static List<DenialReportResponseDTO.Report> toReports(
            List<FacilityReport> reports,
            Long viewerUserId
    ) {
        return reports.stream()
                .map(report -> toReport(report, viewerUserId))
                .toList();
    }

    public static DenialReportResponseDTO.DenialAlert toDenialAlert(FacilityReport report) {
        return new DenialReportResponseDTO.DenialAlert(
                new DenialReportResponseDTO.FacilityRef(
                        report.getFacility().getFacilityId(),
                        report.getFacility().getName()
                ),
                new DenialReportResponseDTO.AlertReport(
                        report.getReportId(),
                        report.getDenialReason(),
                        report.getCreatedAt()
                )
        );
    }
}
