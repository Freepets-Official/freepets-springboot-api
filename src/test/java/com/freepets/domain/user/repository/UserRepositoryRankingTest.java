package com.freepets.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.config.JpaAuditingConfig;

import jakarta.persistence.EntityManager;

/**
 * {@link UserRepository#findNationalRanking}(RANK() 윈도우 함수 + 네이티브 쿼리)과 순위 계산용
 * 카운트 메서드를 검증한다. 목으로는 윈도우 함수 문법이나 동점 처리(1,1,3)가 맞는지 확인할
 * 수 없어 실제 DB(H2, Postgres 모드)에 넣고 돌린다 — XpEventRepositoryGroupedCountTest와
 * 같은 이유.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:userRanking;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS freepets",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserRepositoryRankingTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User user(
            String nickname,
            long totalXp,
            int level
    ) {
        User user = User.builder()
                .email(nickname + "@test.com")
                .passwordHash("encodedPassword")
                .nickname(nickname)
                .provider(Provider.LOCAL)
                .build();
        user.gainXp(totalXp, level);
        entityManager.persist(user);
        return user;
    }

    @Test
    @DisplayName("동점자는 같은 순위를 받고 다음 순위가 건너뛰어진다(1,1,3)")
    void 동점자는_같은_순위를_받고_다음_순위가_건너뛰어진다() {
        User first = user("1등", 300L, 5);
        User tiedA = user("동점A", 200L, 4);
        User tiedB = user("동점B", 200L, 4);
        User last = user("4등", 100L, 3);
        entityManager.flush();
        entityManager.clear();

        List<UserRepository.RankingRow> result = userRepository.findNationalRanking(1L, 10, 0);

        assertThat(result).hasSize(4);
        assertThat(result.get(0).getId()).isEqualTo(first.getId());
        assertThat(result.get(0).getRnk()).isEqualTo(1);
        // 동점자 둘은 같은 순위(2)를 받고, id 오름차순으로 정렬된다.
        assertThat(result.get(1).getRnk()).isEqualTo(2);
        assertThat(result.get(2).getRnk()).isEqualTo(2);
        assertThat(result.get(1).getId()).isLessThan(result.get(2).getId());
        // 다음 순위는 3이 아니라 4로 건너뛴다(RANK()의 정의 그대로).
        assertThat(result.get(3).getId()).isEqualTo(last.getId());
        assertThat(result.get(3).getRnk()).isEqualTo(4);
    }

    @Test
    @DisplayName("size·offset으로 페이지네이션된다")
    void size_offset으로_페이지네이션된다() {
        user("1등", 300L, 5);
        user("2등", 200L, 4);
        user("3등", 100L, 3);
        entityManager.flush();
        entityManager.clear();

        List<UserRepository.RankingRow> firstPage = userRepository.findNationalRanking(1L, 2, 0);
        List<UserRepository.RankingRow> secondPage = userRepository.findNationalRanking(1L, 2, 2);

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(1);
        assertThat(firstPage.get(0).getNickname()).isEqualTo("1등");
        assertThat(secondPage.get(0).getNickname()).isEqualTo("3등");
    }

    @Test
    @DisplayName("탈퇴한 유저는 랭킹·참여자 수에서 빠진다")
    void 탈퇴한_유저는_랭킹에서_빠진다() {
        user("남은사람", 100L, 3);
        User withdrawn = user("탈퇴한사람", 999L, 10);
        entityManager.flush();
        withdrawn.withdraw();
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.countByDeletedAtIsNullAndTotalXpGreaterThanEqual(1L)).isEqualTo(1L);
        List<UserRepository.RankingRow> result = userRepository.findNationalRanking(1L, 10, 0);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNickname()).isEqualTo("남은사람");
    }

    @Test
    @DisplayName("XP가 0인 계정은 랭킹·참여자 수에서 빠진다")
    void XP가_0인_계정은_랭킹에서_빠진다() {
        // #152 — RANK()는 동점을 한 덩어리로 묶으므로, 빼지 않으면 활동이 전혀 없는 계정들이
        // 전부 같은 순위로 목록 뒤를 채운다(실제로 37명 중 33명이 이 상태였다).
        user("활동한사람", 100L, 3);
        user("가입만한사람1", 0L, 1);
        user("가입만한사람2", 0L, 1);
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.countByDeletedAtIsNullAndTotalXpGreaterThanEqual(1L)).isEqualTo(1L);
        List<UserRepository.RankingRow> result = userRepository.findNationalRanking(1L, 10, 0);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNickname()).isEqualTo("활동한사람");
    }

    @Test
    @DisplayName("나보다 totalXp가 많은 활성 계정 수로 내 순위를 계산할 수 있다")
    void 나보다_totalXp가_많은_활성_계정_수로_내_순위를_계산할_수_있다() {
        user("1등", 300L, 5);
        user("2등", 200L, 4);
        User me = user("나", 100L, 3);
        entityManager.flush();
        entityManager.clear();

        long countAheadOfMe = userRepository.countByDeletedAtIsNullAndTotalXpGreaterThan(me.getTotalXp());

        assertThat(countAheadOfMe).isEqualTo(2L);
    }
}
