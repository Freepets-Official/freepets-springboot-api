package com.freepets.domain.petcheck.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.freepets.domain.petcheck.entity.PetCheckResult;

public class PetCheckResponseDTO {

    private PetCheckResponseDTO() {}

    public record VerdictDetail(
            Long petId,
            PetCheckResult result,
            String reason,
            List<String> conditions,

            // 동반 출입증 QR이 가리킬 코드. 프론트는 이 값을 그대로 QR에 담으면 되고
            // 직접 계산할 필요가 없다 — GET /verify/{code} 참고.
            String verifyCode
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

    // GET /verify/{code} 렌더링용 — 아이가 삭제됐으면 null(판별 세션 자체는 유지되므로).
    public record VerifyPetInfo(
            String name,
            String species,
            BigDecimal weight,
            String breedSizeLabel,
            boolean isVaccinated,
            LocalDate vaccinationDate
    ) {}

    // GET /verify/{code} 렌더링용 — 판별 시점에 저장된 값을 그대로 보여준다(조건 원문·확인
    // 시각은 예외로 시설의 최신 값을 쓴다, 지금 시설이 게시한 원문이 곧 "우리가 쓴 문장"이라는
    // 확인 근거이기 때문).
    public record VerifyPage(
            String verifyCode,
            PetCheckResult result,
            String facilityName,
            VerifyPetInfo pet,
            List<String> conditions,
            String reason,
            String petConditionRaw,
            LocalDateTime confirmedAt,
            LocalDateTime issuedAt
    ) {}
}
