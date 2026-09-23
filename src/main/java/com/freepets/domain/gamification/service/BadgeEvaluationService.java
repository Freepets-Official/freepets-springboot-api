package com.freepets.domain.gamification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.gamification.entity.Badge;
import com.freepets.domain.gamification.entity.BadgeFamily;
import com.freepets.domain.gamification.entity.UserBadge;
import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.gamification.repository.UserBadgeRepository;
import com.freepets.domain.gamification.repository.XpEventRepository;
import com.freepets.domain.user.entity.User;

import lombok.RequiredArgsConstructor;

// 크게 두 경로로 불린다 — ① XpEvent가 하나 저장된 직후 GamificationService.grantXp가 호출
// (evaluateAfterXpEvent, 방금 생긴 이벤트의 sourceType과 관련된 배지만 골라 확인), ② "도움됐어요"
// 표시·도장 저장처럼 XpEvent가 안 생기는 행동 뒤 호출부가 직접 호출(evaluateHelpfulSaviorBadge·
// evaluateStampBadge·evaluateRegionBadge — 셋 다 evaluateFamily를 공유). 매번 전체 배지
// 카탈로그를 다 훑지 않고 관련된 배지만 고른다.
@Service
@RequiredArgsConstructor
@Transactional
public class BadgeEvaluationService {

    private final UserBadgeRepository userBadgeRepository;
    private final XpEventRepository xpEventRepository;
    private final GamificationNotificationService gamificationNotificationService;

    // 이미 보유 중이면 개수 조회 자체를 안 하도록, exists 확인을 count 쿼리보다 먼저 한다 —
    // XpEvent 개수 집계가 매번 값싼 연산은 아니라서다. 같은 sourceType 안에서 6단계(동~다이아)를
    // 전부 확인하지만, achievedCount는 한 sourceType당 한 번만 구해서 재사용한다 — 단계마다
    // 매번 다시 세면 리뷰 작성 1건에 동일한 count 쿼리가 최대 6번 나간다.
    public void evaluateAfterXpEvent(
            User user,
            XpSourceType sourceType
    ) {
        Long achievedCount = null;
        for (Badge badge : Badge.values()) {
            if (badge.getRelatedSourceType() != sourceType) {
                continue;
            }
            if (userBadgeRepository.existsByUser_IdAndBadge(user.getId(), badge)) {
                continue;
            }
            if (achievedCount == null) {
                achievedCount = xpEventRepository.countByUser_IdAndSourceType(user.getId(), sourceType);
            }
            award(user, badge, achievedCount);
        }
    }

    /**
     * "구원자" 계열(HELPFUL_BRONZE~DIAMOND) 평가 — 본인 행동(XpEvent)이 아니라 남이 내 리뷰를
     * "도움됐어요"로 표시하는 게 트리거라 {@link #evaluateAfterXpEvent}의 XpEvent 카운트
     * 방식을 못 쓴다. 리뷰 도메인이 이미 계산해 온 총합(review 도메인 소유 개념 — 이 서비스가
     * ReviewRepository를 직접 참조하지 않는다)을 그대로 받아 임계값만 비교한다.
     *
     * <p>한 번 호출로 여러 단계(예: 총합이 한 번에 60이 되면 HELPFUL_GOLD와 HELPFUL_RUBY 둘
     * 다)가 동시에 부여될 수 있다 — 이미 보유한 낮은 단계가 있어도 더 높은 단계 확인을 막지 않는다.
     */
    public void evaluateHelpfulSaviorBadge(
            User reviewAuthor,
            long totalHelpfulReceived
    ) {
        evaluateFamily(reviewAuthor, BadgeFamily.HELPFUL, totalHelpfulReceived);
    }

    /**
     * 여권 도장(STAMP_BRONZE~DIAMOND) 평가 — stamp 도메인(StampCommandService)이 도장을
     * 저장한 뒤 부른다. 도장 수는 stamp 도메인이 소유한 개념이라 이 서비스가 StampRepository를
     * 직접 참조하지 않고 이미 계산된 값을 받는다({@link #evaluateHelpfulSaviorBadge}와 같은 결).
     */
    public void evaluateStampBadge(
            User user,
            long totalStamps
    ) {
        evaluateFamily(user, BadgeFamily.STAMP, totalStamps);
    }

    /**
     * 정복자(REGION_BRONZE~RUBY, 4단계만) 평가 — 서로 다른 시/군/구 수가 기준이다. 도장 수와
     * 마찬가지로 stamp 도메인이 계산한 값을 받는다.
     */
    public void evaluateRegionBadge(
            User user,
            long distinctRegionCount
    ) {
        evaluateFamily(user, BadgeFamily.REGION, distinctRegionCount);
    }

    /**
     * {@code relatedSourceType}이 없는(XpEvent 기반이 아닌) 패밀리 전부가 공유하는 평가 로직 —
     * "패밀리 + 이미 계산된 누적치"만 받아 그 패밀리 소속 배지를 전부 훑는다. 패밀리마다 단계
     * 수가 달라도(REGION은 4단계뿐) 그대로 동작한다 — {@code Badge.values()}를 필터링만 하기
     * 때문이다.
     */
    private void evaluateFamily(
            User user,
            BadgeFamily family,
            long achievedCount
    ) {
        for (Badge badge : Badge.values()) {
            if (badge.getFamily() != family) {
                continue;
            }
            if (userBadgeRepository.existsByUser_IdAndBadge(user.getId(), badge)) {
                continue;
            }
            award(user, badge, achievedCount);
        }
    }

    private void award(
            User user,
            Badge badge,
            long achievedCount
    ) {
        if (achievedCount < badge.getThreshold()) {
            return;
        }

        userBadgeRepository.save(
                UserBadge.builder()
                        .user(user)
                        .badge(badge)
                        .build()
        );
        gamificationNotificationService.notifyBadgeEarned(user.getId(), badge);
    }
}
