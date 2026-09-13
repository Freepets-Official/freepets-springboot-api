package com.freepets.domain.user.entity;

import java.time.LocalDateTime;

import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 애플이 발급한 refresh token. 계정 삭제 시 애플에 폐기를 요청하려면 이 값이 있어야 한다
// (App Store 심사 지침 5.1.1(v) — Sign in with Apple 사용 앱의 요구사항).
//
// user_id에 유니크 제약을 둔다 — 사용자당 하나면 충분하고, 재로그인하면 새 값으로 갈아끼운다.
// 이 제약이 인덱스 역할까지 해주므로 UserDeviceToken과 달리 @Index를 따로 걸지 않는다.
//
// 값은 평문으로 두지 않는다. 탈취되면 애플 계정 연동을 조작할 수 있어, 컬럼명도 암호문임이
// 드러나게 지었다(AppleTokenCipher 참고).
@Getter
@Entity
@Table(name = "apple_refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppleRefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "encrypted_refresh_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    /**
     * 탈퇴하면서 폐기를 시도한 시각. <b>값이 있으면 아직 폐기되지 못한 행</b>이라는 뜻이고,
     * 재시도 배치가 이 조건으로 대상을 찾는다. 폐기에 성공하면 행 자체가 사라지므로 이 값이
     * 채워진 채로 오래 남아 있다면 계속 실패하고 있다는 신호다.
     */
    @Column(name = "revoke_requested_at")
    private LocalDateTime revokeRequestedAt;

    /** 마지막 실패 사유. 운영자가 원인을 바로 보게 남긴다. */
    @Column(name = "revoke_failed_reason", length = 500)
    private String revokeFailedReason;

    /** 폐기 시도 횟수. 애플이 영구히 거부하는 토큰을 매일 무한정 재시도하지 않기 위해 센다. */
    @Column(name = "revoke_attempt_count", nullable = false)
    private int revokeAttemptCount;

    @Builder
    private AppleRefreshToken(
            User user,
            String encryptedRefreshToken
    ) {
        this.user = user;
        this.encryptedRefreshToken = encryptedRefreshToken;
    }

    /** 재로그인 시 새로 받은 토큰으로 갈아끼운다. 직전 토큰은 더 폐기할 필요가 없다. */
    public void replaceToken(String encryptedRefreshToken) {
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.revokeRequestedAt = null;
        this.revokeFailedReason = null;
        this.revokeAttemptCount = 0;
    }

    /** 폐기에 실패했음을 기록해 재시도 대상으로 남긴다. */
    public void recordRevokeFailure(String reason) {
        this.revokeRequestedAt = LocalDateTime.now();
        this.revokeFailedReason = truncate(reason);
        this.revokeAttemptCount++;
    }

    /** 컬럼 길이를 넘는 예외 메시지가 들어와 저장 자체가 실패하는 일이 없게 자른다. */
    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }
}
