package com.freepets.domain.gamification.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.freepets.domain.gamification.entity.PawAnimal;
import com.freepets.domain.gamification.entity.PawColor;

public class GamificationResponseDTO {

    private GamificationResponseDTO() {}

    // GET /api/v1/me/gamification 응답. xpToNextLevel은 최대 레벨(LevelCurve.MAX_LEVEL)에
    // 닿으면 더 이상 의미가 없어 null(키 생략) — tierBadgeImageUrl도 디자인 리소스가 오기 전까진
    // 항상 null이라 같은 이유로 뺀다. tierAnimal·tierColor는 프론트가 발바닥 아이콘을 직접
    // 그릴 수 있게 구조화된 값으로 내려주고, tierLabel은 그 조합을 바로 쓸 수 있는 문자열이다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MyStatus(
            int level,
            long totalXp,
            Long xpToNextLevel,
            PawAnimal tierAnimal,
            PawColor tierColor,
            String tierLabel,
            String tierBadgeImageUrl,
            boolean levelUpNotificationEnabled,
            List<BadgeSummary> badges
    ) {}

    public record BadgeSummary(
            String code,
            String label,
            String description,
            LocalDateTime earnedAt
    ) {}

    public record NotificationResult(
            boolean levelUpNotificationEnabled
    ) {}

}
