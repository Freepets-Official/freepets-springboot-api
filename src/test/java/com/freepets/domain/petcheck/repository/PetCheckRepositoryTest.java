package com.freepets.domain.petcheck.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.petcheck.entity.PetCheck;
import com.freepets.domain.petcheck.entity.PetCheckResult;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.config.JpaAuditingConfig;

import jakarta.persistence.EntityManager;

/**
 * 사업자 대시보드 홈의 "이번 주 판별" 지표가 쓰는 {@code countByFacilityIdsSince}의 경계값 검증.
 * 서비스 단위테스트(OwnerFacilityQueryServiceTest)는 리포지토리를 mock 처리해 "어떤 since 값을
 * 넘기는지"만 확인하므로, 그 값으로 쿼리가 실제로 올바르게 걸러지는지는 여기서만 검증된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:pet-check;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS freepets",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PetCheckRepositoryTest {

    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 9, 14, 0, 0);

    @Autowired
    private PetCheckRepository petCheckRepository;

    @Autowired
    private EntityManager entityManager;

    private User user;

    @BeforeEach
    void setUp() {
        user = createUser("owner@test.com");
    }

    private User createUser(String email) {
        User user = User.builder()
                .email(email)
                .passwordHash("encodedPassword")
                .nickname("몽이아빠")
                .provider(Provider.LOCAL)
                .build();
        entityManager.persist(user);
        return user;
    }

    private Facility createFacility(String name) {
        Facility facility = Facility.builder()
                .name(name)
                .category(FacilityCategory.CAFE)
                .address("강원 강릉시 창해로 17")
                .lat(new BigDecimal("37.8000000"))
                .lng(new BigDecimal("128.9000000"))
                .petAllowed(PetAllowed.ALLOWED)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
        entityManager.persist(facility);
        return facility;
    }

    private PetCheck createPetCheckAt(Facility facility, LocalDateTime createdAt) {
        PetCheck petCheck = PetCheck.builder()
                .user(user)
                .facility(facility)
                .overall(PetCheckResult.ALLOWED)
                .build();
        entityManager.persist(petCheck);
        entityManager.flush();
        // createdAt은 BaseEntity에 updatable=false로 선언돼 있어(감사 로직이 insert 시에만 값을
        // 채운다는 전제), 엔티티 필드를 바꿔 flush해도 그 컬럼은 UPDATE 문에서 아예 빠진다.
        // 값이 실제로 바뀌게 하려면 JPQL 벌크 업데이트로 우회해야 한다.
        entityManager.createQuery("update PetCheck p set p.createdAt = :createdAt where p.checkId = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", petCheck.getCheckId())
                .executeUpdate();
        return petCheck;
    }

    @Test
    void countByFacilityIdsSince_경계_시각과_같은_판별은_포함하고_그_직전은_제외한다() {
        // 쿼리가 >=를 쓴다 — 주 시작 00:00 정각의 판별도 이번 주여야 한다.
        Facility facility = createFacility("카페 파도살롱");
        createPetCheckAt(facility, SINCE);
        createPetCheckAt(facility, SINCE.minusSeconds(1));
        entityManager.clear();

        List<FacilityPetCheckCount> result = petCheckRepository.countByFacilityIdsSince(
                List.of(facility.getFacilityId()), SINCE
        );

        assertThat(result).extracting(FacilityPetCheckCount::checkCount).containsExactly(1L);
    }

    @Test
    void countByFacilityIdsSince_시설별로_그룹화해서_반환한다() {
        Facility facilityA = createFacility("카페 파도살롱");
        Facility facilityB = createFacility("강릉 중앙시장");
        createPetCheckAt(facilityA, SINCE.plusHours(1));
        createPetCheckAt(facilityA, SINCE.plusHours(2));
        createPetCheckAt(facilityB, SINCE.plusHours(1));
        entityManager.clear();

        List<FacilityPetCheckCount> result = petCheckRepository.countByFacilityIdsSince(
                List.of(facilityA.getFacilityId(), facilityB.getFacilityId()), SINCE
        );

        assertThat(result)
                .extracting(FacilityPetCheckCount::facilityId, FacilityPetCheckCount::checkCount)
                .containsExactlyInAnyOrder(
                        tuple(facilityA.getFacilityId(), 2L),
                        tuple(facilityB.getFacilityId(), 1L)
                );
    }

    @Test
    void countByFacilityIdsSince_판별이_없는_시설은_결과에_아예_없다() {
        // 호출부(OwnerFacilityQueryService)가 getOrDefault(id, 0L)로 0을 채우는 전제 —
        // "결과에 없다 = 0건"이 실제로 맞는지 확인한다.
        Facility facilityWithChecks = createFacility("카페 파도살롱");
        Facility facilityWithoutChecks = createFacility("신규 매장");
        createPetCheckAt(facilityWithChecks, SINCE.plusHours(1));
        entityManager.clear();

        List<FacilityPetCheckCount> result = petCheckRepository.countByFacilityIdsSince(
                List.of(facilityWithChecks.getFacilityId(), facilityWithoutChecks.getFacilityId()), SINCE
        );

        assertThat(result).extracting(FacilityPetCheckCount::facilityId)
                .containsExactly(facilityWithChecks.getFacilityId());
    }
}
