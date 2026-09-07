package com.freepets.infra.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;

class FcmClientTest {

    @Test
    void messaging이_없으면_아무것도_안_보내고_빈_목록을_반환한다() {
        FcmClient fcmClient = new FcmClient(null);

        List<String> invalidTokens = fcmClient.sendToTokens(List.of("token-1"), "제목", "본문", Map.of());

        assertThat(invalidTokens).isEmpty();
    }

    @Test
    void 토큰이_비어있으면_발송을_시도하지_않는다() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        FcmClient fcmClient = new FcmClient(messaging);

        List<String> invalidTokens = fcmClient.sendToTokens(List.of(), "제목", "본문", Map.of());

        assertThat(invalidTokens).isEmpty();
        verify(messaging, times(0)).sendEach(anyList());
    }

    @Test
    void 무효_토큰만_골라_반환한다() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        BatchResponse response = mock(BatchResponse.class);

        SendResponse success = mock(SendResponse.class);
        when(success.isSuccessful()).thenReturn(true);

        // exceptionWithCode(...) 자체가 when(...).thenReturn(...)을 쓰기 때문에, 아래 미완성
        // when() 체인의 인자로 바로 넘기면(중첩 호출) Mockito의 스터빙 레코더가 꼬여
        // UnfinishedStubbingException이 난다 — 그래서 미리 로컬 변수로 빼둔다.
        var unregisteredException = exceptionWithCode(MessagingErrorCode.UNREGISTERED);
        var internalException = exceptionWithCode(MessagingErrorCode.INTERNAL);

        SendResponse unregistered = mock(SendResponse.class);
        when(unregistered.isSuccessful()).thenReturn(false);
        when(unregistered.getException()).thenReturn(unregisteredException);

        // 토큰 자체와는 무관한 일시적 오류 — 정리 대상이 아니다.
        SendResponse transientFailure = mock(SendResponse.class);
        when(transientFailure.isSuccessful()).thenReturn(false);
        when(transientFailure.getException()).thenReturn(internalException);

        when(response.getResponses()).thenReturn(List.of(success, unregistered, transientFailure));
        when(messaging.sendEach(anyList())).thenReturn(response);

        FcmClient fcmClient = new FcmClient(messaging);
        List<String> invalidTokens = fcmClient.sendToTokens(
                List.of("token-ok", "token-unregistered", "token-flaky"),
                "제목",
                "본문",
                Map.of()
        );

        assertThat(invalidTokens).containsExactly("token-unregistered");
    }

    @Test
    void 토큰이_오백개_넘으면_오백개씩_나눠_보낸다() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        BatchResponse response = mock(BatchResponse.class);
        SendResponse success = mock(SendResponse.class);
        when(success.isSuccessful()).thenReturn(true);
        // sendEach가 몇 개를 받든 그만큼의 성공 응답을 돌려주도록 answer로 처리한다.
        when(response.getResponses()).thenAnswer(invocation -> List.of());
        when(messaging.sendEach(anyList())).thenAnswer(invocation -> {
            List<?> messages = invocation.getArgument(0);
            List<SendResponse> responses = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                responses.add(success);
            }
            BatchResponse batchResponse = mock(BatchResponse.class);
            when(batchResponse.getResponses()).thenReturn(responses);
            return batchResponse;
        });

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 750; i++) {
            tokens.add("token-" + i);
        }

        FcmClient fcmClient = new FcmClient(messaging);
        List<String> invalidTokens = fcmClient.sendToTokens(tokens, "제목", "본문", Map.of());

        assertThat(invalidTokens).isEmpty();
        // 500개 + 250개, 두 번으로 나눠 보내야 한다 — sendEach는 한 번에 500개까지만 받는다.
        verify(messaging, times(2)).sendEach(anyList());
    }

    private com.google.firebase.messaging.FirebaseMessagingException exceptionWithCode(MessagingErrorCode code) {
        com.google.firebase.messaging.FirebaseMessagingException exception =
                mock(com.google.firebase.messaging.FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(code);
        return exception;
    }
}
