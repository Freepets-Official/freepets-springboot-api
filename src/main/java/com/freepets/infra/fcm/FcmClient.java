package com.freepets.infra.fcm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * FCM 발송을 감싸는 얇은 클래스. {@code infra/tourapi}의 {@code TourApiClient}와 같은 방식으로
 * Spring에 의존하지 않는 POJO다 — {@code FirebaseConfig}가 빈으로 등록한다.
 *
 * <p>{@code messaging}이 {@code null}이면(서비스 계정 키가 없거나 손상돼 초기화 실패) 모든
 * 발송 메서드가 조용히 아무 일도 안 한다 — 푸시는 부가 기능이라 이 문제로 서버 기동이나
 * 다른 기능이 막히면 안 된다.
 */
@Slf4j
public class FcmClient {

    private final FirebaseMessaging messaging;

    public FcmClient(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    // sendEach 한 번에 보낼 수 있는 최대 메시지 수. SDK가 이 이상은 IllegalArgumentException으로
    // 바로 거부한다(FirebaseMessagingException이 아니라 아래 catch로 못 잡는다) — 그래서 애초에
    // 이 크기로 나눠 보낸다. 유저 한 명이 여러 기기(폰+태블릿, 재설치로 기존 토큰 정리 전 중복 등)를
    // 등록할 수 있어 수신자 상한(MAX_RECIPIENTS)보다 토큰 수가 쉽게 더 많아질 수 있다.
    private static final int MAX_MESSAGES_PER_BATCH = 500;

    /**
     * @return 응답 결과 더 이상 유효하지 않다고 확인된 토큰 목록. 호출자가 DB에서 정리해야 한다.
     *         {@code messaging}이 없거나 {@code tokens}가 비어있으면 아무것도 안 보내고 빈 목록을 반환한다.
     */
    public List<String> sendToTokens(
            List<String> tokens,
            String title,
            String body,
            Map<String, String> data
    ) {
        if (messaging == null || tokens.isEmpty()) {
            return List.of();
        }

        Notification notification = Notification.builder().setTitle(title).setBody(body).build();

        List<String> invalidTokens = new ArrayList<>();
        for (List<String> batch : partition(tokens, MAX_MESSAGES_PER_BATCH)) {
            invalidTokens.addAll(sendBatch(batch, notification, data));
        }
        return invalidTokens;
    }

    private List<String> sendBatch(
            List<String> tokens,
            Notification notification,
            Map<String, String> data
    ) {
        // MulticastMessage + sendEachForMulticast는 이 SDK 버전(9.10.0)에서 deprecated라
        // 토큰마다 Message를 만들어 sendEach(List<Message>)로 보낸다 — 응답 모양(BatchResponse,
        // 요청 순서와 1:1 대응하는 SendResponse 목록)은 동일해서 아래 무효 토큰 판별 로직은 그대로 쓴다.
        //
        // Message.Builder.setToken(String)도 이 버전에서 deprecated고 대안은 setFid(String)인데,
        // fid는 FCM 등록 토큰이 아니라 Firebase Installations SDK가 발급하는 별개의 식별자다.
        // 이 앱(Expo 기반) 클라이언트는 등록 토큰만 발급하므로 setFid로 바꾸는 건 대체가 아니라
        // 다른 타겟팅 방식으로의 이관이라 지금 범위 밖이다 — 경고를 억제하고 토큰 방식을 유지한다.
        List<Message> messages = tokens.stream()
                .map(token -> buildMessage(token, notification, data))
                .toList();

        try {
            BatchResponse response = messaging.sendEach(messages);
            return invalidTokensOf(tokens, response);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 발송 중 오류가 발생했습니다.", e);
            return List.of();
        }
    }

    private List<List<String>> partition(
            List<String> tokens,
            int size
    ) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i += size) {
            batches.add(tokens.subList(i, Math.min(i + size, tokens.size())));
        }
        return batches;
    }

    @SuppressWarnings("deprecation") // setToken(String) — 위 sendToTokens 주석 참고
    private Message buildMessage(
            String token,
            Notification notification,
            Map<String, String> data
    ) {
        return Message.builder()
                .setToken(token)
                .setNotification(notification)
                .putAllData(data)
                .build();
    }

    private List<String> invalidTokensOf(
            List<String> tokens,
            BatchResponse response
    ) {
        List<String> invalidTokens = new ArrayList<>();
        List<SendResponse> responses = response.getResponses();

        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (!sendResponse.isSuccessful() && isTokenInvalid(sendResponse.getException())) {
                invalidTokens.add(tokens.get(i));
            }
        }

        return invalidTokens;
    }

    // 토큰 자체가 더 이상 유효하지 않다는 뜻의 에러만 정리 대상으로 본다 — 일시적인 네트워크·서버
    // 오류까지 무효로 간주해 지우면, 잠깐 실패했을 뿐인 멀쩡한 토큰을 잃는다.
    private boolean isTokenInvalid(FirebaseMessagingException exception) {
        if (exception == null) {
            return false;
        }
        MessagingErrorCode code = exception.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT;
    }
}
