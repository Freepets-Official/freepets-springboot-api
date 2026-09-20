package com.freepets.domain.stamp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.stamp.entity.Stamp;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.config.JpaAuditingConfig;

import java.time.LocalDateTime;

/**
 * countByUser_IdAndIsVerifiedOnSiteTrue가 Stamp.isVerifiedOnSite 필드명과 어긋나면
 * GET /api/v1/me/stamps가 500이 난다(리포트: "도장 목록 조회가 500") — 목으로는 파생 쿼리
 * 파싱 오류를 잡을 수 없어 H2에 넣고 돌린다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:stamprepo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS freepets",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class StampRepositoryTest {

    @Autowired
    private StampRepository stampRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FacilityRepository facilityRepository;

    private User user;
    private Facility facility;

    @BeforeEach
    void setUp() {
        user = userRepository.save(
                User.builder()
                        .email("test@freepets.com")
                        .passwordHash("hash")
                        .nickname("테스터")
                        .provider(Provider.LOCAL)
                        .build()
        );
        facility = facilityRepository.save(
                Facility.builder()
                        .name("테스트 시설")
                        .category(FacilityCategory.TOUR)
                        .petAllowed(PetAllowed.ALLOWED)
                        .source(FacilitySource.BUSINESS_SELF)
                        .build()
        );
    }

    @Test
    void countByUser_IdAndIsVerifiedOnSiteTrue_현장확인된_도장만_센다() {
        Stamp onSite = Stamp.builder()
                .user(user)
                .facility(facility)
                .facilityName(facility.getName())
                .isVerifiedOnSite(true)
                .stampedAt(LocalDateTime.now())
                .build();
        stampRepository.save(onSite);

        long count = stampRepository.countByUser_IdAndIsVerifiedOnSiteTrue(user.getId());

        assertThat(count).isEqualTo(1);
    }

}
