package com.freepets.infra.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.freepets.global.config.FirebaseConfig;

/**
 * 실제 FCM 토큰 하나에 테스트 푸시를 보내는 탐사용 일회성 테스트.
 *
 * <p>{@code src/main/resources/firebase-service-account.json}이 실제로 유효한지, 발송이
 * 정말 도착하는지를 수동으로 확인하는 용도라 {@code test} 태스크에서는 실행되지 않는다.
 * 전용 태스크로만 돌린다.
 *
 * <pre>
 * ./gradlew fcmProbe -Dfcm.probe.token=실제토큰값
 * </pre>
 *
 * <p>대상 토큰은 프론트에 아직 토큰 발급 코드가 없어(#POST /api/v1/users/push-tokens 호출부
 * 미구현) Firebase 콘솔 "테스트 메시지 보내기" 등으로 직접 발급해 넘긴다.
 */
@EnabledIfSystemProperty(
        named = "fcm.probe",
        matches = "true",
        disabledReason = "탐사 전용 테스트. ./gradlew fcmProbe -Dfcm.probe.token=... 로 실행한다."
)
class FcmProbeTest {

    @Test
    void 실제_토큰으로_발송하면_무효_토큰으로_돌아오지_않는다() {
        String token = System.getProperty("fcm.probe.token");
        if (token == null || token.isBlank()) {
            fail("fcm.probe.token 시스템 프로퍼티가 필요합니다. "
                    + "예) ./gradlew fcmProbe -Dfcm.probe.token=실제토큰값");
        }

        FcmClient fcmClient = new FirebaseConfig().fcmClient();

        List<String> invalidTokens = fcmClient.sendToTokens(
                List.of(token),
                "FreePets FCM 프로브",
                "이 메시지가 보이면 서비스 계정 키와 발송 경로가 정상입니다.",
                Map.of("probe", "true")
        );

        assertThat(invalidTokens).isEmpty();
    }
}
