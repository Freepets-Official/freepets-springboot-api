package com.freepets.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
