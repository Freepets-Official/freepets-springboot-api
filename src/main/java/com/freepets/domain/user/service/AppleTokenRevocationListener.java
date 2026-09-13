package com.freepets.domain.user.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.freepets.domain.user.event.UserWithdrawnEvent;

import lombok.RequiredArgsConstructor;

/**
 * 탈퇴 이벤트를 받아 애플에 토큰 폐기를 요청한다
 * (App Store 심사 지침 5.1.1(v) — Sign in with Apple 사용 앱의 계정 삭제 요구사항).
 *
 * <p><b>{@code AFTER_COMMIT}인 이유</b> — 폐기는 되돌릴 수 없다. 탈퇴 트랜잭션 안에서 폐기하면,
 * 뒤이은 정리 작업이 실패해 트랜잭션이 롤백됐을 때 <b>사용자는 여전히 회원인데 애플 연결만
 * 끊긴</b> 상태가 된다. 그 계정은 애플 로그인이 막히고 서버가 스스로 되돌릴 방법이 없다.
 * {@code CoursePresetCacheInvalidationListener}와 같은 구조의 판단이다.
 *
 * <p>덤으로 애플과의 통신(최대 10초)이 트랜잭션 밖에서 일어나 DB 커넥션을 오래 잡지 않는다.
 *
 * <p>애플 계정이 아닌 회원은 보관된 토큰이 없어 서비스가 즉시 돌아온다 — 여기서 제공자를
 * 따로 가리지 않는다. 그러려면 User를 다시 조회해야 하는데, 어차피 토큰 행 조회 한 번이면
 * 끝나는 일이다.
 */
@Component
@RequiredArgsConstructor
public class AppleTokenRevocationListener {

    private final AppleRefreshTokenService appleRefreshTokenService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserWithdrawn(UserWithdrawnEvent event) {
        appleRefreshTokenService.revokeForWithdrawal(event.userId());
    }

}
