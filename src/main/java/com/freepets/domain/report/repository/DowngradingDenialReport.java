package com.freepets.domain.report.repository;

import java.time.LocalDateTime;

import com.freepets.domain.report.entity.DenialReason;

/**
 * 시설의 신뢰도를 내리고 있는 실시간 거부 제보 한 건.
 *
 * <p>건수만 세지 않고 행으로 받는다 — 사업자 대시보드 홈이 매장마다 "거부 제보 N건"과 최신 제보 한 줄을
 * 함께 그려서, 집계와 최신 1건을 따로 조회하면 쿼리가 둘이 된다.
 */
public record DowngradingDenialReport(
        Long facilityId,
        DenialReason denialReason,
        LocalDateTime reportedAt
) {}
