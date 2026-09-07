package com.freepets.domain.report.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.report.entity.DenialReason;
import com.freepets.domain.user.entity.UserDeviceToken;
import com.freepets.domain.user.repository.UserDeviceTokenRepository;
import com.freepets.infra.fcm.FcmClient;

import lombok.RequiredArgsConstructor;

/**
 * 거부 제보가 접수되는 순간, 그 시설을 판별했던 다른 유저들에게 실시간 푸시를 보낸다.
 *
 * <p>{@code @Async} 메서드를 {@link DenialReportCommandService}가 아니라 별도 클래스에 둔
 * 이유: 같은 클래스 안에서 {@code @Async} 메서드를 호출하면 프록시를 거치지 않아 비동기가
 * 안 걸리는 Spring 셀프 인보케이션 문제가 있다 — {@code report(...)}의 응답이 발송을
 * 기다리며 늦어지면 안 되므로 반드시 별도 빈으로 분리해야 한다.
 */
@Service
@RequiredArgsConstructor
public class DenialReportNotificationService {

    // 시설 하나를 아주 많은 유저가 판별했을 경우를 대비한 상한 — PetCheckRepository의
    // findDistinctFacilityIdsByUser_Id(#53, 반대 방향 쿼리)와 같은 이유로 둔다.
    private static final int MAX_RECIPIENTS = 200;

    private final PetCheckRepository petCheckRepository;
    private final UserDeviceTokenRepository userDeviceTokenRepository;
    private final FcmClient fcmClient;

    @Async("notificationExecutor")
    @Transactional
    public void notifyDenial(
            Long facilityId,
            Long reporterId,
            DenialReason reason,
            String facilityName
    ) {
        List<Long> recipientUserIds = petCheckRepository.findDistinctUserIdsByFacility_FacilityId(
                facilityId,
                reporterId,
                PageRequest.of(0, MAX_RECIPIENTS)
        );
        if (recipientUserIds.isEmpty()) {
            return;
        }

        List<UserDeviceToken> deviceTokens = userDeviceTokenRepository.findAllByUser_IdIn(recipientUserIds);
        if (deviceTokens.isEmpty()) {
            return;
        }

        List<String> tokens = deviceTokens.stream().map(UserDeviceToken::getToken).toList();

        List<String> invalidTokens = fcmClient.sendToTokens(
                tokens,
                facilityName + "에서 방금 거부됐어요",
                reason.getLabel() + " 사유로 현장 거부 제보가 접수됐어요.",
                Map.of("facilityId", String.valueOf(facilityId))
        );

        if (!invalidTokens.isEmpty()) {
            userDeviceTokenRepository.deleteAllByTokenIn(invalidTokens);
        }
    }
}
