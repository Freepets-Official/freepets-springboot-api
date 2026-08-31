package com.freepets.domain.petcheck.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.freepets.domain.petcheck.entity.PetCheckResult;

public class PetCheckResponseDTO {

    private PetCheckResponseDTO() {}

    public record VerdictDetail(
            Long petId,
            PetCheckResult result,
            String reason,
            List<String> conditions
    ) {}

    // POST /api/v1/ai/check 응답 — 방금 만든 판별 세션의 상세(아이별 결과 포함)
    public record CheckResult(
            Long checkId,
            Long facilityId,
            PetCheckResult overall,
            List<VerdictDetail> verdicts
    ) {}

    // GET /api/v1/pet-checks 목록 항목 — 이력 화면용 요약(아이별 상세는 안 담음)
    public record CheckHistoryItem(
            Long checkId,
            Long facilityId,
            List<Long> petIds,
            PetCheckResult overall,
            LocalDateTime createdAt
    ) {}

    public record CheckHistoryList(
            List<CheckHistoryItem> items,
            long total
    ) {}
}
