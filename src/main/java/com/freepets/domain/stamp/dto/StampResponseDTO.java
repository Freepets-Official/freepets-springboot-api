package com.freepets.domain.stamp.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class StampResponseDTO {

    private StampResponseDTO() {}

    // GET /api/v1/me/stamps의 stamps[], POST /api/v1/me/stamps의 result 양쪽이 공유하는
    // 도장 한 건의 모양.
    public record StampSummary(
            Long stampId,
            Long facilityId,
            String facilityName,
            String sido,
            String sigungu,
            String sidoCode,
            String sigunguCode,
            List<Long> petIds,
            String photoUrl,

            // record 접근자가 isVerifiedOnSite()이므로 JSON 프로퍼티명을 verifiedOnSite로 명시 고정
            // (freepets-docs docs/14-도장-서버-저장.md 계약과 그대로 맞춘다).
            @JsonProperty("verifiedOnSite")
            boolean isVerifiedOnSite,
            LocalDateTime createdAt
    ) {}

    // POST /api/v1/me/stamps 응답. completionOrder/completedAt은 isRegionCompleted가 아니면
    // 키 자체가 빠진다(GamificationResponseDTO.MyRanking.rank와 같은 이유 — 의미 없는 값을
    // null/0으로 채우지 않는다).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StampResult(
            Long stampId,
            Long facilityId,
            String facilityName,
            String sido,
            String sigungu,
            String sidoCode,
            String sigunguCode,
            List<Long> petIds,
            String photoUrl,

            @JsonProperty("verifiedOnSite")
            boolean isVerifiedOnSite,
            LocalDateTime createdAt,
            boolean isNew,

            // record 접근자가 isRegionCompleted()이므로 JSON 프로퍼티명을 regionCompleted로 명시
            // 고정.
            @JsonProperty("regionCompleted")
            boolean isRegionCompleted,
            Long completionOrder,
            LocalDateTime completedAt
    ) {}

    // GET /api/v1/me/stamps 응답.
    public record MyStamps(
            List<StampSummary> stamps,
            Summary summary
    ) {}

    public record Summary(
            long total,
            long regionCount,
            long onSiteCount,
            long thisMonthCount
    ) {}

}
