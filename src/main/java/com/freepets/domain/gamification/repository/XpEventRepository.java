package com.freepets.domain.gamification.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.gamification.entity.XpEvent;
import com.freepets.domain.gamification.entity.XpSourceType;

public interface XpEventRepository extends JpaRepository<XpEvent, Long> {

    // 하루 지급 상한 판단(GamificationService) — 오늘 자정 이후 같은 유형으로 몇 번 지급받았는지.
    long countByUser_IdAndSourceTypeAndCreatedAtGreaterThanEqual(
            Long userId,
            XpSourceType sourceType,
            LocalDateTime since
    );

    // 평생 1회 지급 판단(코스 공개 등) — 이 (user, type, sourceId) 조합으로 이미 지급한 적이 있는지.
    boolean existsByUser_IdAndSourceTypeAndSourceId(
            Long userId,
            XpSourceType sourceType,
            Long sourceId
    );

    // BadgeEvaluationService의 개수 기반 배지 조건(예: 리뷰 10개) 판정용.
    long countByUser_IdAndSourceType(
            Long userId,
            XpSourceType sourceType
    );

}
