package com.freepets.domain.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.report.entity.DenialReason;
import com.freepets.domain.report.entity.FacilityReport;
import com.freepets.domain.report.entity.ReportStatus;
import com.freepets.domain.report.entity.ReportType;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.config.JpaAuditingConfig;

import jakarta.persistence.EntityManager;

/**
 * 사업자 대시보드의 거부 제보 하향 판정 쿼리(홈의 배지·경고 카드, denial-alerts 전체 조회) 검증.
 *
 * <p>{@code countDowngradingByFacilityIds}/{@code findLatestDowngradingByFacilityIds}/
 * {@code findDowngradingByFacilityId} 세 쿼리는 "확정({@code confirmedAt}) 이후에 들어온 실시간
 * 거부 제보만 신뢰도를 내린다"는 같은 기준을 공유한다. 서비스 단위테스트(OwnerFacilityQueryServiceTest,
 * OwnerFacilityConditionCommandServiceTest)는 이 리포지토리를 전부 mock 처리하므로, WHERE 절이
 * 실제 DB에서 이 기준대로 걸러내는지는 여기서만 검증된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:facility-report;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS freepets",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class FacilityReportRepositoryTest {

    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 9, 10, 0, 0);

    @Autowired
    private FacilityReportRepository facilityReportRepository;

    @Autowired
    private EntityManager entityManager;

    private User reporter;

    @BeforeEach
    void setUp() {
        reporter = createUser("reporter@test.com");
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

    private Facility createFacility(String name, LocalDateTime confirmedAt) {
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
        if (confirmedAt != null) {
            ReflectionTestUtils.setField(facility, "confirmedAt", confirmedAt);
        }
        return facility;
    }

    private void createReportAt(
            Facility facility,
            LocalDateTime createdAt,
            boolean isRealtime,
            DenialReason reason
    ) {
        FacilityReport report = FacilityReport.builder()
                .user(reporter)
                .facility(facility)
                .reportType(ReportType.DENIED)
                .denialReason(reason)
                .status(ReportStatus.APPLIED)
                .isRealtime(isRealtime)
                .build();
        entityManager.persist(report);
        entityManager.flush();
        // createdAt은 BaseEntity에 updatable=false로 선언돼 있어 엔티티 필드를 바꿔 flush해도
        // UPDATE 문에서 그 컬럼이 아예 빠진다. JPQL 벌크 업데이트로 우회해야 실제로 값이 바뀐다.
        entityManager.createQuery("update FacilityReport r set r.createdAt = :createdAt where r.reportId = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", report.getReportId())
                .executeUpdate();
    }

    @Test
    void countDowngradingByFacilityIds_확정_이전_제보는_세지_않는다() {
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 9, 15, 9, 0);
        Facility facility = createFacility("카페 파도살롱", confirmedAt);
        createReportAt(facility, confirmedAt.minusHours(1), true, DenialReason.WEIGHT);
        createReportAt(facility, confirmedAt.plusHours(1), true, DenialReason.WEIGHT);
        entityManager.clear();

        List<FacilityDenialReportCount> result = facilityReportRepository.countDowngradingByFacilityIds(
                List.of(facility.getFacilityId()), SINCE
        );

        assertThat(result).extracting(FacilityDenialReportCount::reportCount).containsExactly(1L);
    }

    @Test
    void countDowngradingByFacilityIds_확정한_적_없으면_전부_포함한다() {
        Facility facility = createFacility("신규 매장", null);
        createReportAt(facility, SINCE.plusDays(1), true, DenialReason.INDOOR);
        createReportAt(facility, SINCE.plusDays(2), true, DenialReason.INDOOR);
        entityManager.clear();

        List<FacilityDenialReportCount> result = facilityReportRepository.countDowngradingByFacilityIds(
                List.of(facility.getFacilityId()), SINCE
        );

        assertThat(result).extracting(FacilityDenialReportCount::reportCount).containsExactly(2L);
    }

    @Test
    void countDowngradingByFacilityIds_실시간이_아닌_제보는_제외한다() {
        Facility facility = createFacility("카페 파도살롱", null);
        createReportAt(facility, SINCE.plusDays(1), false, DenialReason.OTHER);
        entityManager.clear();

        List<FacilityDenialReportCount> result = facilityReportRepository.countDowngradingByFacilityIds(
                List.of(facility.getFacilityId()), SINCE
        );

        assertThat(result).isEmpty();
    }

    @Test
    void countDowngradingByFacilityIds_rolling_윈도우_이전_제보는_제외한다() {
        Facility facility = createFacility("카페 파도살롱", null);
        createReportAt(facility, SINCE.minusDays(1), true, DenialReason.CROWDED);
        entityManager.clear();

        List<FacilityDenialReportCount> result = facilityReportRepository.countDowngradingByFacilityIds(
                List.of(facility.getFacilityId()), SINCE
        );

        assertThat(result).isEmpty();
    }

    @Test
    void findLatestDowngradingByFacilityIds_같은_기준으로_최신_한_건만_반환한다() {
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 9, 15, 9, 0);
        Facility facility = createFacility("카페 파도살롱", confirmedAt);
        createReportAt(facility, confirmedAt.minusHours(1), true, DenialReason.WEIGHT); // 확정 이전 — 제외
        createReportAt(facility, confirmedAt.plusHours(1), true, DenialReason.BREED); // 더 오래된 하향 제보
        createReportAt(facility, confirmedAt.plusHours(2), true, DenialReason.INDOOR); // 가장 최신
        entityManager.clear();

        List<DowngradingDenialReport> result = facilityReportRepository.findLatestDowngradingByFacilityIds(
                List.of(facility.getFacilityId()), SINCE
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).denialReason()).isEqualTo(DenialReason.INDOOR);
    }

    @Test
    void findDowngradingByFacilityId_확정_이후_제보만_최신순으로_반환한다() {
        // GET /owner/facilities/{id}/denial-alerts가 쓰는 단건 조회 — 홈의 배지 계산과 같은
        // 기준(confirmedAt 이후)을 써야 건수와 목록이 어긋나지 않는다.
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 9, 15, 9, 0);
        Facility facility = createFacility("카페 파도살롱", confirmedAt);
        createReportAt(facility, confirmedAt.minusHours(1), true, DenialReason.WEIGHT); // 확정 이전 — 제외
        createReportAt(facility, confirmedAt.plusHours(1), true, DenialReason.BREED);
        createReportAt(facility, confirmedAt.plusHours(2), true, DenialReason.INDOOR);
        entityManager.clear();

        List<FacilityReport> result = facilityReportRepository.findDowngradingByFacilityId(
                facility.getFacilityId(), SINCE
        );

        assertThat(result).extracting(FacilityReport::getDenialReason)
                .containsExactly(DenialReason.INDOOR, DenialReason.BREED);
    }
}
