package com.freepets.domain.gamification.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.freepets.domain.gamification.entity.PawAnimal;
import com.freepets.domain.gamification.entity.PawColor;
import com.freepets.domain.gamification.entity.XpSourceType;

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
            List<BadgeSummary> badges,
            List<BadgeProgress> progress
    ) {}

    public record BadgeSummary(
            String code,
            String label,
            String description,
            LocalDateTime earnedAt
    ) {}

    /**
     * 패밀리(예: 리뷰) 단위 진행도 — 획득한 배지만 담는 {@link BadgeSummary}와 달리, 아직 못
     * 받은 단계까지 포함한 전체 6단계를 보여준다. {@code count}가 지금 누적 횟수라 프론트가
     * "다음 단계까지 N회 남음"을 계산할 수 있다.
     *
     * @param family 패밀리 코드(예: {@code "REVIEW"}) — {@link com.freepets.domain.gamification.entity.BadgeFamily} 이름
     * @param label  패밀리 한글 이름(예: "리뷰")
     * @param count  이 패밀리의 현재 누적 횟수
     * @param tiers  동→다이아 6단계, 낮은 단계부터 순서대로
     */
    public record BadgeProgress(
            String family,
            String label,
            long count,
            List<TierProgress> tiers
    ) {}

    /**
     * @param tier      단계 코드(예: {@code "GOLD"}) — {@link com.freepets.domain.gamification.entity.BadgeTier} 이름
     * @param threshold 이 단계를 달성하는 누적 횟수
     * @param earnedAt  이 단계를 이미 획득했으면 그 시각, 아직이면 {@code null}(잠긴 단계)
     */
    public record TierProgress(
            String tier,
            int threshold,
            LocalDateTime earnedAt
    ) {}

    public record NotificationResult(
            boolean levelUpNotificationEnabled
    ) {}

    // GET /api/v1/me/gamification/quests 응답. resetsAt은 다른 모든 타임스탬프처럼
    // LocalDateTime(UTC)+"Z"로 직렬화된다(JacksonConfig 참고) — 다음 KST 자정을 UTC로 환산한 값.
    public record QuestList(
            LocalDateTime resetsAt,
            List<Quest> quests
    ) {}

    /**
     * @param completed     오늘 이 sourceType으로 XP를 받은 횟수
     * @param target        이 sourceType의 하루 상한({@link XpSourceType#getDailyCap()}과 동일) —
     *                      completed는 실제 지급 상한과 같은 기준으로 세므로 target을 넘을 수 없다
     * @param earnedXpToday 오늘 이 sourceType으로 실제 지급된 XP 합계
     */
    public record Quest(
            XpSourceType sourceType,
            String label,
            long completed,
            int target,
            long earnedXpToday
    ) {}

}
