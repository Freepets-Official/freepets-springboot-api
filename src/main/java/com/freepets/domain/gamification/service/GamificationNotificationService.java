package com.freepets.domain.gamification.service;

import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.freepets.domain.gamification.entity.Badge;
import com.freepets.domain.user.entity.UserDeviceToken;
import com.freepets.domain.user.repository.UserDeviceTokenRepository;
import com.freepets.infra.fcm.FcmClient;

import lombok.RequiredArgsConstructor;

/**
 * 레벨업·배지 획득 푸시. {@code DenialReportNotificationService}와 같은 구조 —
 * {@code @Async} 메서드를 호출부(GamificationService)와 분리해서 Spring 셀프 인보케이션 문제를
 * 피하고, 원래 액션(리뷰 작성 등)의 응답이 푸시 발송을 기다리며 늦어지지 않게 한다.
 *
 * <p>레벨업 알림 on/off 토글({@code User.levelUpNotificationEnabled})은 여기가 아니라 호출부가
 * 미리 확인한다 — 꺼져 있으면 이 클래스를 호출조차 하지 않아 비동기 홉 자체를 아낀다. 배지
 * 획득 알림도 같은 토글을 함께 쓴다(기획 결정엔 레벨업만 명시돼 있었지만, 토글을 따로 두 개
 * 만들 근거가 아직 없어 하나로 묶었다 — 필요해지면 분리 가능).
 */
@Service
@RequiredArgsConstructor
public class GamificationNotificationService {

    private final UserDeviceTokenRepository userDeviceTokenRepository;
    private final FcmClient fcmClient;

    // DenialReportNotificationService와 같은 이유로 @Transactional을 일부러 안 둔다 — FCM 발송은
    // 외부 HTTP 호출이라 DB 커넥션을 오래 붙잡으면 안 된다.
    @Async("notificationExecutor")
    public void notifyLevelUp(
            Long userId,
            int newLevel
    ) {
        send(
                userId,
                "레벨 " + newLevel + "이 됐어요!",
                "꾸준히 활동해주셔서 레벨이 올랐어요.",
                Map.of("level", String.valueOf(newLevel))
        );
    }

    @Async("notificationExecutor")
    public void notifyBadgeEarned(
            Long userId,
            Badge badge
    ) {
        send(
                userId,
                "새 배지 획득: " + badge.getLabel(),
                badge.getDescription(),
                Map.of("badge", badge.name())
        );
    }

    private void send(
            Long userId,
            String title,
            String body,
            Map<String, String> data
    ) {
        List<UserDeviceToken> deviceTokens = userDeviceTokenRepository.findAllByUser_IdIn(List.of(userId));
        if (deviceTokens.isEmpty()) {
            return;
        }

        List<String> tokens = deviceTokens.stream().map(UserDeviceToken::getToken).toList();
        List<String> invalidTokens = fcmClient.sendToTokens(tokens, title, body, data);

        if (!invalidTokens.isEmpty()) {
            userDeviceTokenRepository.deleteAllByTokenIn(invalidTokens);
        }
    }
}
