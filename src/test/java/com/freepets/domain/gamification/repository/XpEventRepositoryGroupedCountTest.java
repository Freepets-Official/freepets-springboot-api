package com.freepets.domain.gamification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.freepets.domain.gamification.entity.XpEvent;
import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.config.JpaAuditingConfig;

import jakarta.persistence.EntityManager;

/**
 * {@link XpEventRepository#countGroupedByUser_Id}(인터페이스 프로젝션 + GROUP BY) 검증.
 *
 * <p>목으로는 JPQL 문법 자체가 맞는지 확인할 수 없다 — 실제 DB(H2, Postgres 모드)에 넣고 돌려야
 * 프로젝션 별칭·GROUP BY가 제대로 동작하는지 확인된다(ReviewRepositoryAggregateTest와 같은 이유).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:xpEventGroupedCount;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS freepets",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class XpEventRepositoryGroupedCountTest {

    @Autowired
    private XpEventRepository xpEventRepository;

    @Autowired
    private EntityManager entityManager;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("owner@test.com")
                .passwordHash("encodedPassword")
                .nickname("몽이아빠")
                .provider(Provider.LOCAL)
                .build();
        entityManager.persist(user);
    }

    private void saveEvent(
            XpSourceType sourceType,
            long sourceId
    ) {
        entityManager.persist(
                XpEvent.builder()
                        .user(user)
                        .sourceType(sourceType)
                        .sourceId(sourceId)
                        .amount(5)
                        .build()
        );
    }

    @Test
    @DisplayName("소스타입별 개수를 그룹 쿼리 한 번으로 가져온다")
    void 소스타입별_개수를_그룹_쿼리_한_번으로_가져온다() {
        saveEvent(XpSourceType.PETCHECK, 1L);
        saveEvent(XpSourceType.PETCHECK, 2L);
        saveEvent(XpSourceType.PETCHECK, 3L);
        saveEvent(XpSourceType.REVIEW, 4L);
        entityManager.flush();
        entityManager.clear();

        List<XpEventRepository.SourceTypeCount> result = xpEventRepository.countGroupedByUser_Id(user.getId());

        Map<XpSourceType, Long> counts = result.stream()
                .collect(java.util.stream.Collectors.toMap(
                        XpEventRepository.SourceTypeCount::getSourceType,
                        XpEventRepository.SourceTypeCount::getCount
                ));
        assertThat(counts).hasSize(2);
        assertThat(counts.get(XpSourceType.PETCHECK)).isEqualTo(3L);
        assertThat(counts.get(XpSourceType.REVIEW)).isEqualTo(1L);
    }

    @Test
    @DisplayName("아무 XpEvent도 없으면 빈 목록을 반환한다")
    void 아무_XpEvent도_없으면_빈_목록을_반환한다() {
        List<XpEventRepository.SourceTypeCount> result = xpEventRepository.countGroupedByUser_Id(user.getId());

        assertThat(result).isEmpty();
    }
}
