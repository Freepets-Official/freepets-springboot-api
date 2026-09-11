package com.freepets.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    /**
     * 소셜 로그인의 유일한 조회 경로. 이메일은 바뀌거나 없을 수 있어 식별자로 쓰지 않는다.
     * 결과가 있으면 로그인, 없으면 신규 가입으로 갈린다.
     */
    Optional<User> findByProviderAndProviderId(
            Provider provider,
            String providerId
    );

    /**
     * {@code GamificationService.grantXp} 전용 — 같은 유저에 대한 동시 경험치 지급 요청을
     * 행 단위로 직렬화한다. 잠금 없이 조회하면 두 요청이 같은 totalXp를 읽고 각자 갱신해
     * 서로의 결과를 덮어쓸 수 있다(lost update).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /**
     * 계정 조회·수정·탈퇴 등 "본인 계정" 엔드포인트 전용 — 이미 탈퇴한 유저는 findById로는
     * 여전히 찾아지지만(행 자체는 남아있음), 탈퇴한 계정을 조회·수정·재탈퇴할 수 있게 두면
     * 안 되므로 이 메서드로 존재하지 않는 것처럼 취급한다.
     */
    Optional<User> findByIdAndDeletedAtIsNull(Long id);
}
