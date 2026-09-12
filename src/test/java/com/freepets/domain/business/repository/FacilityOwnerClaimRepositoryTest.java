package com.freepets.domain.business.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.config.JpaAuditingConfig;

import jakarta.persistence.EntityManager;

/**
 * 소유 기록 조회 쿼리와 "한 시설 = 한 사업자" 제약 검증. 목으로는 잡을 수 없어 H2에 넣고 돌린다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// BaseEntity의 createdAt/updatedAt은 not null이라 감사 설정이 없으면 저장 자체가 안 된다.
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:owner-claim;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS freepets",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class FacilityOwnerClaimRepositoryTest {

    @Autowired
    private FacilityOwnerClaimRepository facilityOwnerClaimRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = createUser("owner@test.com");
    }

    private User createUser(String email) {
        User user = User.builder()
                .email(email)
                .passwordHash("encodedPassword")
                .nickname("사장님")
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

    private FacilityOwnerClaim createClaim(
            User user,
            Facility facility
    ) {
        return FacilityOwnerClaim.builder()
                .user(user)
                .facility(facility)
                .maskedBusinessNumber("123-45-*****")
                .verifiedAt(LocalDateTime.of(2026, 9, 12, 10, 0))
                .build();
    }

    @Test
    void findFacilityIdsByUserId_소유_기록이_없으면_빈_목록을_반환한다() {
        assertThat(facilityOwnerClaimRepository.findFacilityIdsByUserId(owner.getId())).isEmpty();
    }

    @Test
    void findFacilityIdsByUserId_본인_소유_시설만_소유_기록이_생긴_순서대로_반환한다() {
        Facility firstFacility = createFacility("카페 파도살롱");
        Facility secondFacility = createFacility("강릉 중앙시장");
        Facility otherOwnerFacility = createFacility("옆 가게");
        User otherOwner = createUser("other@test.com");

        entityManager.persist(createClaim(owner, firstFacility));
        entityManager.persist(createClaim(otherOwner, otherOwnerFacility));
        entityManager.persist(createClaim(owner, secondFacility));
        entityManager.flush();
        entityManager.clear();

        assertThat(facilityOwnerClaimRepository.findFacilityIdsByUserId(owner.getId()))
                .containsExactly(firstFacility.getFacilityId(), secondFacility.getFacilityId());
    }

    @Test
    void save_이미_소유자가_있는_시설이면_유니크_제약에_걸린다() {
        Facility facility = createFacility("카페 파도살롱");
        User otherOwner = createUser("other@test.com");
        facilityOwnerClaimRepository.saveAndFlush(createClaim(owner, facility));

        assertThatThrownBy(() -> facilityOwnerClaimRepository.saveAndFlush(createClaim(otherOwner, facility)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
