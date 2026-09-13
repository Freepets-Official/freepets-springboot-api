package com.freepets.domain.user.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freepets.domain.user.event.UserWithdrawnEvent;

@ExtendWith(MockitoExtension.class)
class AppleTokenRevocationListenerTest {

    @Mock
    private AppleRefreshTokenService appleRefreshTokenService;

    @InjectMocks
    private AppleTokenRevocationListener appleTokenRevocationListener;

    @Test
    void 탈퇴_이벤트를_받으면_애플_토큰_폐기를_요청한다() {
        appleTokenRevocationListener.onUserWithdrawn(new UserWithdrawnEvent(7L));

        verify(appleRefreshTokenService).revokeForWithdrawal(7L);
    }
}
