package com.freepets.domain.gamification.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.gamification.entity.XpEvent;
import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.gamification.repository.XpEventRepository;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

/**
 * 게이미피케이션 진입점. 기존 도메인 서비스(판별·리뷰·제보·만족도·코스)가 자기 저장 로직이 끝난
 * 직후 {@link #grantXp} 한 줄만 호출하면 되고, 나머지(하루 상한·평생 1회 중복 방지·레벨 계산·
 * 레벨업 알림·배지 재평가)는 전부 여기서 처리한다.
 *
 * <p>상한 초과나 중복 지급은 예외를 던지지 않고 조용히 스킵한다 — 이 메서드를 호출한 원래 액션
 * (리뷰 작성 등)은 경험치 지급 여부와 무관하게 그대로 성공해야 하므로, 이 리포의 다른
 * idempotent 패턴(CalendarMedLogCommandService의 유니크 충돌 시 조용히 성공 처리 등)과 같은 결이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GamificationService {

    // 기획 결정: "도메인별 하루 상한" — 구체 값은 엔지니어링 제안(LevelCurve와 같은 성격의 상수).
    // REVIEW는 시설당 리뷰가 1개라 자연히 제한되고, 이 표에 없는 sourceType은 하루 상한이 없는
    // 것으로 취급한다.
    //
    // COURSE_PUBLISHED는 "평생 1회"가 courseId 단위라 같은 시설을 스톱으로 재사용하는 트리비얼한
    // 코스를 무한정 새로 만들어 공개하면(코스 공개 게이트가 스톱마다 판별·리뷰는 확인하지만 그
    // 시설을 다른 코스에서 이미 썼는지는 확인하지 않는다) 하루 상한 없이 XP를 무제한으로 쌓을 수
    // 있었다 — 그래서 다른 도메인처럼 하루 상한을 둔다.
    private static final Map<XpSourceType, Integer> DAILY_CAP = Map.of(
            XpSourceType.PETCHECK, 10,
            XpSourceType.REPORT, 5,
            XpSourceType.SATISFACTION, 5,
            XpSourceType.COURSE_PUBLISHED, 5,
            XpSourceType.COURSE_SHARED_COPY, 10
    );

    private final UserRepository userRepository;
    private final XpEventRepository xpEventRepository;
    private final GamificationNotificationService gamificationNotificationService;
    private final BadgeEvaluationService badgeEvaluationService;

    public void grantXp(
            Long userId,
            XpSourceType sourceType,
            Long sourceId,
            int amount
    ) {
        if (isAlreadyGrantedForSource(userId, sourceType, sourceId)) {
            return;
        }
        if (isDailyCapReached(userId, sourceType)) {
            return;
        }

        // 호출부가 이미 존재 확인을 마친 userId만 넘기므로 방어적으로만 처리한다 — 못 찾으면
        // 지급 없이 조용히 리턴(이 메서드가 원래 액션의 성공 여부에 영향을 주면 안 되므로 예외로
        // 올리지 않는다).
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        xpEventRepository.save(
                XpEvent.builder()
                        .user(user)
                        .sourceType(sourceType)
                        .sourceId(sourceId)
                        .amount(amount)
                        .build()
        );

        long newTotalXp = user.getTotalXp() + amount;
        int newLevel = LevelCurve.levelForTotalXp(newTotalXp);
        boolean isLeveledUp = user.gainXp(amount, newLevel);

        if (isLeveledUp && user.isLevelUpNotificationEnabled()) {
            gamificationNotificationService.notifyLevelUp(userId, newLevel);
        }

        badgeEvaluationService.evaluateAfterXpEvent(user, sourceType);
    }

    /**
     * PATCH /api/v1/me/gamification/notification — 레벨업(및 배지) 알림 on/off. 다른 도메인
     * 처럼 "본인 것만" 걱정할 필요가 없다 — 대상이 항상 인증된 본인(userId)뿐이라 소유권 검증이
     * 따로 필요 없다.
     */
    public boolean updateLevelUpNotification(
            Long userId,
            boolean enabled
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        user.toggleLevelUpNotification(enabled);
        return user.isLevelUpNotificationEnabled();
    }

    private boolean isAlreadyGrantedForSource(
            Long userId,
            XpSourceType sourceType,
            Long sourceId
    ) {
        return sourceId != null
                && xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(userId, sourceType, sourceId);
    }

    private boolean isDailyCapReached(
            Long userId,
            XpSourceType sourceType
    ) {
        Integer dailyCap = DAILY_CAP.get(sourceType);
        if (dailyCap == null) {
            return false;
        }

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        long todayCount = xpEventRepository
                .countByUser_IdAndSourceTypeAndCreatedAtGreaterThanEqual(userId, sourceType, startOfToday);
        return todayCount >= dailyCap;
    }
}
