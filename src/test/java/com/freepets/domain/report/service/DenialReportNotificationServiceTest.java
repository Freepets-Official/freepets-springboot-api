package com.freepets.domain.report.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.report.entity.DenialReason;
import com.freepets.domain.user.entity.UserDeviceToken;
import com.freepets.domain.user.repository.UserDeviceTokenRepository;
import com.freepets.infra.fcm.FcmClient;

@ExtendWith(MockitoExtension.class)
class DenialReportNotificationServiceTest {

    @Mock
    private PetCheckRepository petCheckRepository;

    @Mock
    private UserDeviceTokenRepository userDeviceTokenRepository;

    @Mock
    private FcmClient fcmClient;

    @InjectMocks
    private DenialReportNotificationService denialReportNotificationService;

    private UserDeviceToken deviceToken(String token) {
        return UserDeviceToken.builder()
                .token(token)
                .platform("ANDROID")
                .build();
    }

    @Test
    void 판별했던_다른_유저가_없으면_토큰_조회도_발송도_하지_않는다() {
        when(petCheckRepository.findDistinctUserIdsByFacility_FacilityId(eq(7L), eq(1L), any(Pageable.class)))
                .thenReturn(List.of());

        denialReportNotificationService.notifyDenial(7L, 1L, DenialReason.WEIGHT, "테스트 시설");

        verifyNoInteractions(userDeviceTokenRepository);
        verifyNoInteractions(fcmClient);
    }

    @Test
    void 판별했던_유저는_있지만_등록된_토큰이_없으면_발송하지_않는다() {
        when(petCheckRepository.findDistinctUserIdsByFacility_FacilityId(eq(7L), eq(1L), any(Pageable.class)))
                .thenReturn(List.of(2L, 3L));
        when(userDeviceTokenRepository.findAllByUser_IdIn(List.of(2L, 3L))).thenReturn(List.of());

        denialReportNotificationService.notifyDenial(7L, 1L, DenialReason.WEIGHT, "테스트 시설");

        verifyNoInteractions(fcmClient);
    }

    @Test
    void 토큰이_있으면_알림을_보내고_무효_토큰만_정리한다() {
        when(petCheckRepository.findDistinctUserIdsByFacility_FacilityId(eq(7L), eq(1L), any(Pageable.class)))
                .thenReturn(List.of(2L, 3L));
        when(userDeviceTokenRepository.findAllByUser_IdIn(List.of(2L, 3L)))
                .thenReturn(List.of(deviceToken("token-2"), deviceToken("token-3")));
        when(fcmClient.sendToTokens(eq(List.of("token-2", "token-3")), any(), any(), any()))
                .thenReturn(List.of("token-3"));

        denialReportNotificationService.notifyDenial(7L, 1L, DenialReason.WEIGHT, "테스트 시설");

        verify(fcmClient).sendToTokens(
                eq(List.of("token-2", "token-3")),
                eq("테스트 시설에서 방금 거부됐어요"),
                eq("체중 초과 사유로 현장 거부 제보가 접수됐어요."),
                eq(Map.of("facilityId", "7"))
        );
        verify(userDeviceTokenRepository).deleteAllByTokenIn(List.of("token-3"));
    }

    @Test
    void 무효_토큰이_없으면_정리를_호출하지_않는다() {
        when(petCheckRepository.findDistinctUserIdsByFacility_FacilityId(eq(7L), eq(1L), any(Pageable.class)))
                .thenReturn(List.of(2L));
        when(userDeviceTokenRepository.findAllByUser_IdIn(List.of(2L)))
                .thenReturn(List.of(deviceToken("token-2")));
        when(fcmClient.sendToTokens(anyList(), any(), any(), any()))
                .thenReturn(List.of());

        denialReportNotificationService.notifyDenial(7L, 1L, DenialReason.WEIGHT, "테스트 시설");

        verify(userDeviceTokenRepository, never()).deleteAllByTokenIn(any());
    }
}
