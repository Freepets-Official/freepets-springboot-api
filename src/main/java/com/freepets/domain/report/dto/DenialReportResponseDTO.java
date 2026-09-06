package com.freepets.domain.report.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.freepets.domain.report.entity.DenialReason;
import com.freepets.domain.report.entity.ReportStatus;
import com.freepets.domain.report.entity.ReportType;

public class DenialReportResponseDTO {

    private DenialReportResponseDTO() {}

    // POST/recent/mine 공용 — 제보 1건.
    public record Report(
            Long reportId,
            Long facilityId,
            ReportType type,
            // "현장 거부 · 체중 초과" 형태의 표시 문구. 저장하지 않고 reason으로부터 매번 만든다.
            String content,
            DenialReason reason,

            // 사진·AI 검증(2단계, 별도 이슈)이 있어야 값이 달라지는 필드다. 지금은 실시간
            // 제보만 있어서 항상 고정값 — 현장에서 바로 보낸 제보는 사후 기억보다 정확하다고
            // 보고 사진 없이도 가중치 2를 준다(프론트 app-store.tsx의 기존 정책과 동일).
            int weight,
            boolean hasEvidence,

            // CLAUDE.md의 boolean 네이밍 규칙(is + camelCase)을 따르려고 is를 붙였다. record는
            // 컴포넌트 이름을 그대로 JSON 키로 쓰므로(실측 확인: PetResponseDTO의 isVaccinated
            // 주석과 달리 여기선 안 줄여지고 "isMine"으로 그대로 나감) 프론트가 이미 mine/realtime
            // (is 없이)으로 쓰고 있는 기존 응답 규격을 지키려면 @JsonProperty로 되돌려야 한다.
            @JsonProperty("mine") boolean isMine,
            @JsonProperty("realtime") boolean isRealtime,
            ReportStatus status,
            LocalDateTime createdAt
    ) {}

    // GET /me/denial-alerts 응답 한 행.
    public record FacilityRef(
            Long facilityId,
            String name
    ) {}

    public record AlertReport(
            Long reportId,
            DenialReason reason,
            LocalDateTime createdAt
    ) {}

    public record DenialAlert(
            FacilityRef facility,
            AlertReport report
    ) {}
}
