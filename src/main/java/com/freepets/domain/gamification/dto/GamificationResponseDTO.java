package com.freepets.domain.gamification.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.freepets.domain.gamification.entity.PawAnimal;
import com.freepets.domain.gamification.entity.PawColor;
import com.freepets.domain.gamification.entity.PawFinish;
import com.freepets.domain.gamification.entity.XpSourceType;

public class GamificationResponseDTO {

    private GamificationResponseDTO() {}

    // GET /api/v1/me/gamification 응답. xpToNextLevel은 최대 레벨(LevelCurve.MAX_LEVEL)에
    // 닿으면 더 이상 의미가 없어 null(키 생략) — tierBadgeImageUrl도 디자인 리소스가 오기 전까진
    // 항상 null이라 같은 이유로 뺀다. tierAnimal·tierFinish·tierColor는 프론트가 발바닥 아이콘을
    // 직접 그릴 수 있게 구조화된 값으로 내려주고, tierLabel은 그 조합을 바로 쓸 수 있는 문자열이다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MyStatus(
            int level,
            long totalXp,
            Long xpToNextLevel,
            PawAnimal tierAnimal,
            PawFinish tierFinish,
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

    // GET /api/v1/gamification/ranking 응답. 지금은 scope가 항상 "NATION"이다 — 시/도·시/군/구
    // 스코프는 유저 단위로 "어느 지역 활동인지"를 아직 기록하지 않아서 이번 범위 밖이다(활동
    // 지역 기반으로 가려면 XP 적립 시점에 시설의 지역 코드를 XpEvent에 같이 남겨야 한다).
    // updatedAt은 스냅샷이 아니라 조회 시점 그대로다 — 실시간 계산이라 항상 "지금 기준"이다.
    public record RankingResult(
            String scope,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            MyRanking me,
            List<RankingItem> items,
            long total,
            LocalDateTime updatedAt
    ) {}

    /**
     * @param rank              1부터. 참여자가 너무 적으면(ranked=false) 순위 자체를 감춰야
     *                          해서 키가 생략된다 — GamificationRankingQueryService의 최소
     *                          인원 기준 참고
     * @param participantCount  이 스코프의 전체 참여자 수 — "340명 중 12번째" 문장에 그대로 쓴다
     * @param ranked            participantCount가 너무 적어 순위 공개가 무의미하거나(1명)
     *                          참여자 특정이 쉬워지면(2명) false — 이때 rank는 생략된다
     */
    public record MyRanking(
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Long rank,
            long participantCount,
            long xp,
            int level,
            PawAnimal tierAnimal,
            PawFinish tierFinish,
            PawColor tierColor,
            boolean ranked
    ) {}

    public record RankingItem(
            long rank,
            Long userId,
            String nickname,
            long xp,
            int level,
            PawAnimal tierAnimal,
            PawFinish tierFinish,
            PawColor tierColor,
            boolean isMe
    ) {}

}
