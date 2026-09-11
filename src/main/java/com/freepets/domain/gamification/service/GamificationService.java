package com.freepets.domain.gamification.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
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
import lombok.extern.slf4j.Slf4j;

/**
 * 게이미피케이션 진입점. 기존 도메인 서비스(판별·리뷰·제보·만족도·코스)가 자기 저장 로직이 끝난
 * 직후 {@link #grantXp} 한 줄만 호출하면 되고, 나머지(하루 상한·평생 1회 중복 방지·레벨 계산·
 * 레벨업 알림·배지 재평가)는 전부 여기서 처리한다.
 *
 * <p>상한 초과나 중복 지급은 예외를 던지지 않고 조용히 스킵한다 — 이 메서드를 호출한 원래 액션
 * (리뷰 작성 등)은 경험치 지급 여부와 무관하게 그대로 성공해야 하므로, 이 리포의 다른
 * idempotent 패턴(CalendarMedLogCommandService의 유니크 충돌 시 조용히 성공 처리 등)과 같은 결이다.
 */
@Slf4j
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

    // 서버 프로세스는 항상 UTC로 고정돼 있다(FreepetsServerApplication의 static 블록) —
    // "하루"의 경계는 서버 타임존이 아니라 실제 사용자가 있는 KST 기준이어야 한다.
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

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
        // 유저 행을 잠그기 전에 값싼 사전 검사부터 한다 — 이미 상한/중복으로 막힐 호출
        // (예: 판별을 하루에 10번 넘게 반복하는 흔한 케이스)이 매번 User row를 잠글 필요는
        // 없다. 그래도 이 사전 검사만으로는 두 요청이 동시에 통과해버릴 수 있어(아래 참고),
        // 잠금을 잡은 뒤 같은 검사를 한 번 더 한다.
        if (isAlreadyGrantedForSource(userId, sourceType, sourceId)) {
            return;
        }
        if (isDailyCapReached(userId, sourceType)) {
            return;
        }

        // 여기서부터 유저 행을 잠가 같은 유저에 대한 동시 지급 요청을 직렬화한다 — 잠금 없이
        // totalXp를 읽고 갱신하면, 두 요청이 같은 값을 읽어 서로의 결과를 덮어쓸 수 있다
        // (lost update). 호출부가 이미 존재 확인을 마친 userId만 넘기므로 못 찾는 경우는
        // 방어적으로만 처리한다 — 지급 없이 조용히 리턴(이 메서드가 원래 액션의 성공 여부에
        // 영향을 주면 안 되므로 예외로 올리지 않는다).
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null) {
            return;
        }
        // 잠금을 잡기 전에 두 요청이 동시에 위 사전 검사를 통과했을 수 있으므로, 직렬화된
        // 상태에서 같은 검사를 다시 한다 — 여기서는 앞선 요청이 커밋한 결과가 보인다.
        if (isAlreadyGrantedForSource(userId, sourceType, sourceId) || isDailyCapReached(userId, sourceType)) {
            return;
        }

        try {
            xpEventRepository.save(
                    XpEvent.builder()
                            .user(user)
                            .sourceType(sourceType)
                            .sourceId(sourceId)
                            .amount(amount)
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            // uk_xp_events_user_source가 막아준 마지막 방어선 — 두 검사 사이에도 남는 레이스를
            // 여기서 잡는다. 원래 액션은 그대로 성공해야 하므로 예외를 올리지 않는다.
            log.warn("이미 지급된 경험치라 스킵합니다 — userId={}, sourceType={}, sourceId={}", userId, sourceType, sourceId);
            return;
        }

        long newTotalXp = user.getTotalXp() + amount;
        int newLevel = LevelCurve.levelForTotalXp(newTotalXp);
        boolean isLeveledUp = user.gainXp(newTotalXp, newLevel);

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

        long todayCount = xpEventRepository
                .countByUser_IdAndSourceTypeAndCreatedAtGreaterThanEqual(userId, sourceType, startOfTodayInBusinessZone());
        return todayCount >= dailyCap;
    }

    /**
     * "오늘 자정"을 KST 기준으로 계산해, {@code createdAt}(서버가 항상 UTC로 고정해 저장하는
     * naive LocalDateTime)과 같은 좌표로 맞춰 돌려준다. 서버 프로세스 타임존(UTC)을 그대로
     * 썼다면 하루 상한이 자정이 아니라 오전 9시(KST)에 풀렸을 것이다.
     */
    private LocalDateTime startOfTodayInBusinessZone() {
        return LocalDate.now(BUSINESS_ZONE)
                .atStartOfDay(BUSINESS_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
