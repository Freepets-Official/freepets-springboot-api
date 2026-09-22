package com.freepets.domain.user.repository;

import java.util.List;
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

    /**
     * 두 가지로 쓴다(#152).
     *
     * <ul>
     *   <li>내 순위 — "나보다 totalXp가 많은 활성 계정 수 + 1"이 곧 내 순위다. 동점은 같은
     *       순위를 받아야 해서(1,1,3) "많거나 같은 수"가 아니라 "많은 수"만 센다.</li>
     *   <li>참여자 수 — 0을 넘겨 "XP가 1이라도 있는 활성 계정 수"를 센다. 활동이 없는 계정은
     *       랭킹 목록에서도 빠지므로({@link #findNationalRanking}) 참여자 수 기준도 같아야
     *       "N명 중 K번째"가 목록과 맞는다.</li>
     * </ul>
     *
     * <p>지역 스코프가 생기면 지역 필터가 추가된 버전이 따로 필요하다.
     */
    long countByDeletedAtIsNullAndTotalXpGreaterThan(long totalXp);

    /**
     * 전국 랭킹 상위 목록 — RANK() 윈도우 함수로 동점자는 같은 순위를 받고 다음 순위가
     * 건너뛰어지게(1,1,3) DB에서 직접 계산한다. 애플리케이션에서 "몇 번째 행인지"로 순위를
     * 매기면 동점 구간에서 실제 순위와 어긋난다. 동점자끼리는 id 오름차순(먼저 가입한 순)으로
     * 안정적인 순서를 준다 — "먼저 도달한 사람이 앞"을 정확히 재현할 별도 시각 기록이 아직
     * 없어서 쓰는 근사치다.
     *
     * <p>XP가 0인 계정은 제외한다(#152) — 활동이 없으면 순위도 없다. 동점을 같은 순위로 묶는
     * RANK() 특성상, 빼지 않으면 활동이 전혀 없는 계정이 전부 한 덩어리로 목록 뒤를 채운다.
     *
     * <p>{@code freepets.users}로 스키마를 명시한다 — {@link UserRepositoryRankingTest}로
     * 확인해보니 이 레포의 H2 테스트 DB·(추정)실제 배포 DB 모두 {@code freepets} 스키마를
     * 쓰고 있고, 스키마를 떼면(unqualified {@code FROM users}) 그 자리에서 SQLGrammarException이
     * 난다 — 즉 하드코딩된 스키마 자체는 이 환경과 맞다. 라이브 500 보고(2026-09-20, 프론트)의
     * 원인은 이 스키마 불일치가 아닌 다른 요인(배포 DB의 실제 스키마/권한, 컬럼 별칭 매핑 등)일
     * 가능성이 높다 — 백엔드가 실제 배포 환경에서 직접 재현해 원인을 좁혀야 한다.
     */
    @Query(value = """
            SELECT * FROM (
                SELECT id, nickname, total_xp AS totalXp, level,
                       RANK() OVER (ORDER BY total_xp DESC) AS rnk
                FROM freepets.users
                WHERE deleted_at IS NULL AND total_xp > 0
            ) ranked
            ORDER BY rnk ASC, id ASC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<RankingRow> findNationalRanking(
            @Param("size") int size,
            @Param("offset") long offset
    );

    interface RankingRow {
        Long getId();

        String getNickname();

        long getTotalXp();

        int getLevel();

        long getRnk();
    }
}
