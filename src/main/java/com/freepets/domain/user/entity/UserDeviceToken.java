package com.freepets.domain.user.entity;

import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 푸시 발송용 디바이스 토큰(FCM). 거부 제보 알림 전용이 아니라 범용 저장소로 둔다 —
// 나중에 다른 알림(접종 기한 등, 프론트 notifications.tsx에 이미 항목이 보임)에도 그대로 쓴다.
//
// user_id에 인덱스를 둔다 — findAllByUser_IdIn(DenialReportNotificationService)이 이 컬럼으로
// 조회한다. 운영 DB가 PostgreSQL(Supabase)이라 FK 컬럼이라고 자동으로 인덱스가 붙지 않는다.
@Getter
@Entity
@Table(
        name = "user_device_tokens",
        indexes = @Index(name = "idx_user_device_tokens_user_id", columnList = "user_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDeviceToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 토큰 자체를 유니크 키로 둔다 — 기기 재설치·다른 계정 로그인 시 같은 토큰이 예전 소유자에게
    // 남아 있으면 안 되므로, 등록할 때마다 이 토큰을 가진 기존 행을 지우고 새로 저장한다
    // (UserCommandService.registerPushToken 참고).
    @Column(nullable = false, unique = true, length = 255)
    private String token;

    // 앱이 굳이 안 보내도 되게 nullable — "IOS"/"ANDROID" 정도의 자유 문자열, 지금은 발송 로직이
    // 플랫폼을 구분해 쓰지 않는다(FCM이 안드로이드/iOS 둘 다 같은 토큰 형식으로 처리).
    @Column(length = 20)
    private String platform;

    @Builder
    private UserDeviceToken(
            User user,
            String token,
            String platform
    ) {
        this.user = user;
        this.token = token;
        this.platform = platform;
    }
}
