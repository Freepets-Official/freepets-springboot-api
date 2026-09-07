package com.freepets.global.config;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import com.freepets.infra.fcm.FcmClient;

import lombok.extern.slf4j.Slf4j;

/**
 * 거부 제보 실시간 알림(DenialReportNotificationService)이 쓰는 FCM 발송기를 등록한다.
 *
 * <p>서비스 계정 키({@code src/main/resources/firebase-service-account.json}, gitignore
 * 대상)가 없거나 손상됐어도 서버 기동을 막지 않는다 — 푸시는 부가 기능이라, 구글/애플
 * client-ids가 없으면 기동을 막는 소셜 로그인(#51 계획)과 달리 이 문제로 전체 서비스가
 * 안 뜨면 안 된다. 실패하면 {@link FcmClient}가 이후 모든 발송을 조용히 건너뛴다.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    private static final String SERVICE_ACCOUNT_PATH = "firebase-service-account.json";

    @Bean
    public FcmClient fcmClient() {
        FirebaseMessaging messaging = initMessaging();
        return new FcmClient(messaging);
    }

    private FirebaseMessaging initMessaging() {
        try (InputStream serviceAccount = new ClassPathResource(SERVICE_ACCOUNT_PATH).getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            // 테스트에서 Spring 컨텍스트가 여러 번 만들어질 수 있어(같은 JVM 안에서 여러
            // @WebMvcTest/@SpringBootTest), 이미 초기화돼 있으면 재사용한다 — 안 그러면
            // "FirebaseApp already exists" 예외가 난다.
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();

            return FirebaseMessaging.getInstance(app);
        } catch (IOException e) {
            log.warn(
                    "{}을 읽지 못해 FCM 푸시 발송이 비활성화됩니다. 서버는 정상 기동합니다.",
                    SERVICE_ACCOUNT_PATH,
                    e
            );
            return null;
        }
    }
}
