package com.freepets.domain.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.user.entity.AppleRefreshToken;

public interface AppleRefreshTokenRepository extends JpaRepository<AppleRefreshToken, Long> {

    // 재로그인 시 기존 행을 찾아 토큰만 갈아끼우고, 탈퇴 시 폐기할 값을 꺼낼 때 쓴다.
    Optional<AppleRefreshToken> findByUser_Id(Long userId);

    // 폐기에 성공했거나 애플 설정이 없어 폐기를 시도할 수 없을 때 정리한다.
    void deleteByUser_Id(Long userId);

    // 재시도 배치가 "탈퇴는 됐는데 아직 폐기되지 못한" 행을 모을 때 쓴다 — 폐기에 성공하면
    // 행이 사라지므로, 이 시각이 남아 있다는 것 자체가 미완료를 뜻한다.
    List<AppleRefreshToken> findAllByRevokeRequestedAtIsNotNull();
}
