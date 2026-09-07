package com.freepets.domain.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.user.entity.UserDeviceToken;

public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, Long> {

    // 등록 시 upsert 로직(UserCommandService.registerPushToken)이 먼저 같은 토큰의 기존 행을
    // 지우고 새로 저장한다 — 기기 재설치·다른 계정 로그인 시 예전 소유자에게 발송되는 걸 막는다.
    void deleteByToken(String token);

    // 해제(unregisterPushToken) 시 본인 소유 토큰인지 확인하는 용도.
    Optional<UserDeviceToken> findByToken(String token);

    // DenialReportNotificationService가 "판별 이력 있는 다른 유저들"의 토큰을 한 번에 모을 때 쓴다.
    List<UserDeviceToken> findAllByUser_IdIn(List<Long> userIds);

    // FCM 발송 결과 더 이상 유효하지 않다고 확인된 토큰을 정리할 때 쓴다.
    void deleteAllByTokenIn(List<String> tokens);
}
