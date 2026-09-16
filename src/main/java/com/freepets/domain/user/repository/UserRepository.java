package com.freepets.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.Role;
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

    /**
     * {@code JwtAuthenticationFilter} 전용 — 인증 단계에서 탈퇴한 유저의 토큰을 걸러낸다. 이
     * 필터는 로그인이 필요한 모든 요청에서 돌기 때문에, 역할처럼 필터 전체에 영향이 갈 수 있는
     * 조회는 여기 얹지 않는다({@link #findActiveRoleById} 참고).
     *
     * <p>이 확인이 없으면, 이미 탈퇴한 계정도 (아직 만료되지 않은) 예전 액세스 토큰으로 다른
     * 모든 도메인(리뷰 작성, 코스 공개 등)의 API를 계속 호출할 수 있다 — 각 서비스가 저마다
     * userId를 findById로 조회하기 전에, 인증 경계에서 한 번에 막는다.
     *
     * <p>{@code UserQueryService.isActiveUser}(리프레시 토큰 재발급)도 같은 확인이 필요해 이 메서드를
     * 재사용한다.
     */
    boolean existsByIdAndDeletedAtIsNull(Long id);

    /**
     * {@code SecurityConfig}의 관리자 경로 판정 전용 — 활성 계정의 역할을 읽는다. 탈퇴한 계정이면 비어
     * 있다.
     *
     * <p>{@code /api/v1/admin/**} 요청에서만 부른다. {@code JwtAuthenticationFilter}는 로그인이
     * 필요한 모든 요청에서 도는데, 거기서 역할까지 함께 조회하면 역할 컬럼이나 이 쿼리에 문제가 생겼을 때
     * 앱 전체가 영향을 받는다 — 관리자 경로에서만 조회해 그 위험을 관리자 API로 좁힌다.
     *
     * <p>역할을 토큰에 넣지 않고 매번 여기서 읽어서, 관리자 권한을 주거나 회수하면 재로그인 없이
     * 바로 반영된다.
     */
    @Query("select u.role from User u where u.id = :id and u.deletedAt is null")
    Optional<Role> findActiveRoleById(@Param("id") Long id);
}
