package com.freepets.domain.business.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.business.entity.ClaimStatus;
import com.freepets.domain.business.entity.FacilityOwnerClaim;
import com.freepets.domain.business.entity.RequestedCondition;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.config.JpaAuditingConfig;

import jakarta.persistence.EntityManager;

/**
 * 소유 기록 조회 쿼리와 상태별 조회 검증. 목으로는 잡을 수 없어 H2에 넣고 돌린다.
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

    private static final String CERTIFICATE_URL = "https://bucket.s3.ap-northeast-2.amazonaws.com/certificate.pdf";

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
                .requestedCondition(RequestedCondition.of(
                        PetAllowed.ALLOWED,
                        new BigDecimal("10.00"),
                        true,
                        List.of(Requirement.LEASH),
                        "리드줄 착용 시 실내 동반 가능"
                ))
                .registrationCertificateUrl(CERTIFICATE_URL)
                .build();
    }

    // 상태 전이 메서드는 승인·반려 기능과 함께 붙는다. 그 전까지는 필드에 직접 넣어 상태별 조회를 검증한다.
    private FacilityOwnerClaim createClaim(
            User user,
            Facility facility,
            ClaimStatus status
    ) {
        FacilityOwnerClaim claim = createClaim(user, facility);
        ReflectionTestUtils.setField(claim, "status", status);
        return claim;
    }

    @Test
    void 새로_만든_신청의_상태는_PENDING이고_조건과_등록증이_저장된다() {
        // 신청은 운영자 승인을 기다린다. 조건은 승인될 때 시설에 반영한다.
        FacilityOwnerClaim saved = facilityOwnerClaimRepository.saveAndFlush(
                createClaim(owner, createFacility("카페 파도살롱"))
        );
        entityManager.clear();

        FacilityOwnerClaim found = facilityOwnerClaimRepository.findById(saved.getClaimId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(ClaimStatus.PENDING);
        assertThat(found.getRegistrationCertificateUrl()).isEqualTo(CERTIFICATE_URL);
        assertThat(found.getRequestedCondition().getPetAllowed()).isEqualTo(PetAllowed.ALLOWED);
        assertThat(found.getRequestedCondition().getMaxWeight()).isEqualByComparingTo("10.00");
        assertThat(found.getRequestedCondition().getMaxWeightInclusive()).isTrue();
        assertThat(found.getRequestedCondition().getConditionRaw()).isEqualTo("리드줄 착용 시 실내 동반 가능");
        // JSON 컬럼에 담긴 요구조건이 다시 읽히는지 — 목으로는 잡을 수 없는 부분이다.
        assertThat(found.getRequestedCondition().getRequirements()).containsExactly(Requirement.LEASH);
    }

    @Test
    void existsPendingByFacilityIdAndUserId_본인의_대기_신청만_찾는다() {
        // 같은 사람이 같은 매장에 신청을 쌓지 못하게 막는 확인이다. 남이 낸 대기 신청은 막지 않는다.
        Facility facility = createFacility("카페 파도살롱");
        User otherApplicant = createUser("other@test.com");
        entityManager.persist(createClaim(owner, facility, ClaimStatus.PENDING));
        entityManager.persist(createClaim(owner, createFacility("반려된 매장"), ClaimStatus.REJECTED));
        entityManager.flush();
        entityManager.clear();

        Long facilityId = facility.getFacilityId();
        assertThat(facilityOwnerClaimRepository.existsPendingByFacilityIdAndUserId(facilityId, owner.getId()))
                .isTrue();
        assertThat(facilityOwnerClaimRepository.existsPendingByFacilityIdAndUserId(facilityId, otherApplicant.getId()))
                .isFalse();
    }

    @Test
    void existsPendingByFacilityIdAndUserId_대기가_아닌_기록은_세지_않는다() {
        Facility rejectedFacility = createFacility("반려된 매장");
        entityManager.persist(createClaim(owner, rejectedFacility, ClaimStatus.REJECTED));
        entityManager.flush();
        entityManager.clear();

        assertThat(facilityOwnerClaimRepository.existsPendingByFacilityIdAndUserId(
                rejectedFacility.getFacilityId(),
                owner.getId()
        )).isFalse();
    }

    @Test
    void findApprovedFacilityIdsByUserId_소유_기록이_없으면_빈_목록을_반환한다() {
        assertThat(facilityOwnerClaimRepository.findApprovedFacilityIdsByUserId(owner.getId())).isEmpty();
    }

    @Test
    void findApprovedFacilityIdsByUserId_승인되지_않은_기록은_소유_매장으로_세지_않는다() {
        // 심사 중이거나 반려·해제된 매장이 소유 매장으로 잡히면 승인 전에 사업자 프로필이 붙는다.
        Facility approvedFacility = createFacility("카페 파도살롱");
        entityManager.persist(createClaim(owner, approvedFacility, ClaimStatus.APPROVED));
        entityManager.persist(createClaim(owner, createFacility("대기 매장"), ClaimStatus.PENDING));
        entityManager.persist(createClaim(owner, createFacility("반려 매장"), ClaimStatus.REJECTED));
        entityManager.persist(createClaim(owner, createFacility("해제 매장"), ClaimStatus.REVOKED));
        entityManager.flush();
        entityManager.clear();

        assertThat(facilityOwnerClaimRepository.findApprovedFacilityIdsByUserId(owner.getId()))
                .containsExactly(approvedFacility.getFacilityId());
    }

    @Test
    void findApprovedFacilityIdsByUserId_본인_소유_시설만_소유_기록이_생긴_순서대로_반환한다() {
        Facility firstFacility = createFacility("카페 파도살롱");
        Facility secondFacility = createFacility("강릉 중앙시장");
        Facility otherOwnerFacility = createFacility("옆 가게");
        User otherOwner = createUser("other@test.com");

        entityManager.persist(createClaim(owner, firstFacility, ClaimStatus.APPROVED));
        entityManager.persist(createClaim(otherOwner, otherOwnerFacility, ClaimStatus.APPROVED));
        entityManager.persist(createClaim(owner, secondFacility, ClaimStatus.APPROVED));
        entityManager.flush();
        entityManager.clear();

        assertThat(facilityOwnerClaimRepository.findApprovedFacilityIdsByUserId(owner.getId()))
                .containsExactly(firstFacility.getFacilityId(), secondFacility.getFacilityId());
    }

    @Test
    void save_같은_시설에_승인되지_않은_기록은_여러_개_저장할_수_있다() {
        // "시설당 한 행" 제약이 남아 있으면 누가 신청만 넣어둬도 다른 사람이 신청조차 못 한다(선점).
        // "시설당 승인된 소유자 하나"는 PostgreSQL 조건부 유니크 인덱스라 H2에서는 검증할 수 없다
        // (db/pending-manual-migrations.sql 참고).
        Facility facility = createFacility("카페 파도살롱");
        User otherApplicant = createUser("other@test.com");

        facilityOwnerClaimRepository.saveAndFlush(createClaim(owner, facility, ClaimStatus.APPROVED));
        facilityOwnerClaimRepository.saveAndFlush(createClaim(otherApplicant, facility, ClaimStatus.PENDING));
        facilityOwnerClaimRepository.saveAndFlush(createClaim(otherApplicant, facility, ClaimStatus.REJECTED));

        assertThat(facilityOwnerClaimRepository.count()).isEqualTo(3);
    }

    @Test
    void findApprovedByFacilityId_대기_신청이_여러_개여도_승인된_소유_기록만_찾는다() {
        // 매장 등록이 "이 시설에 이미 승인된 주인이 있는지"를 이 조회로 판단한다. 대기 신청이 여러 건 섞여
        // 있어도 결과가 한 건으로 좁혀져야 한다 — 두 건 이상이면 조회 자체가 실패한다.
        Facility claimedFacility = createFacility("카페 파도살롱");
        User firstApplicant = createUser("first@test.com");
        User secondApplicant = createUser("second@test.com");
        facilityOwnerClaimRepository.saveAndFlush(createClaim(owner, claimedFacility, ClaimStatus.APPROVED));
        facilityOwnerClaimRepository.saveAndFlush(createClaim(firstApplicant, claimedFacility, ClaimStatus.PENDING));
        facilityOwnerClaimRepository.saveAndFlush(createClaim(secondApplicant, claimedFacility, ClaimStatus.PENDING));
        entityManager.clear();

        FacilityOwnerClaim found = facilityOwnerClaimRepository
                .findApprovedByFacilityId(claimedFacility.getFacilityId())
                .orElseThrow();

        assertThat(found.isRequestedBy(owner.getId())).isTrue();
    }

    @Test
    void findApprovedByFacilityId_대기_신청만_있는_시설은_주인이_없다() {
        Facility pendingOnlyFacility = createFacility("옆 가게");
        facilityOwnerClaimRepository.saveAndFlush(createClaim(owner, pendingOnlyFacility, ClaimStatus.PENDING));
        facilityOwnerClaimRepository.saveAndFlush(
                createClaim(createUser("other@test.com"), pendingOnlyFacility, ClaimStatus.PENDING)
        );
        entityManager.clear();

        assertThat(facilityOwnerClaimRepository.findApprovedByFacilityId(pendingOnlyFacility.getFacilityId()))
                .isEmpty();
    }

    @Test
    void findAllWithFacilityByUserIdOrderByCreatedAtDesc_상태와_무관하게_전부_최신순으로_반환한다() {
        // 지난 반려 이력도 화면에서 보여줄 수 있어야 하므로 상태로 거르지 않는다.
        FacilityOwnerClaim firstApplied = facilityOwnerClaimRepository.saveAndFlush(
                createClaim(owner, createFacility("카페 파도살롱"), ClaimStatus.REJECTED)
        );
        FacilityOwnerClaim secondApplied = facilityOwnerClaimRepository.saveAndFlush(
                createClaim(owner, createFacility("강릉 중앙시장"), ClaimStatus.PENDING)
        );
        FacilityOwnerClaim thirdApplied = facilityOwnerClaimRepository.saveAndFlush(
                createClaim(owner, createFacility("옆 가게"), ClaimStatus.APPROVED)
        );
        entityManager.clear();

        assertThat(facilityOwnerClaimRepository.findAllWithFacilityByUserIdOrderByCreatedAtDesc(owner.getId()))
                .extracting(FacilityOwnerClaim::getClaimId)
                .containsExactly(thirdApplied.getClaimId(), secondApplied.getClaimId(), firstApplied.getClaimId());
    }

    @Test
    void findAllWithFacilityByUserIdOrderByCreatedAtDesc_다른_사용자의_신청은_섞이지_않는다() {
        User otherApplicant = createUser("other@test.com");
        entityManager.persist(createClaim(owner, createFacility("카페 파도살롱"), ClaimStatus.PENDING));
        entityManager.persist(createClaim(otherApplicant, createFacility("옆 가게"), ClaimStatus.PENDING));
        entityManager.flush();
        entityManager.clear();

        assertThat(facilityOwnerClaimRepository.findAllWithFacilityByUserIdOrderByCreatedAtDesc(otherApplicant.getId()))
                .extracting(claim -> claim.getFacility().getName())
                .containsExactly("옆 가게");
    }

    @Test
    void findAllWithFacilityByUserIdOrderByCreatedAtDesc_신청이_없으면_빈_목록을_반환한다() {
        assertThat(facilityOwnerClaimRepository.findAllWithFacilityByUserIdOrderByCreatedAtDesc(owner.getId()))
                .isEmpty();
    }

    @Test
    void findByStatus_같은_상태만_오래된_순으로_페이지네이션해서_반환한다() {
        // 관리자 큐는 오래된 신청부터 처리하도록 오름차순이다 — 최신순인 내 신청 목록과 반대다.
        FacilityOwnerClaim first = facilityOwnerClaimRepository.saveAndFlush(
                createClaim(owner, createFacility("카페 파도살롱"), ClaimStatus.PENDING)
        );
        FacilityOwnerClaim second = facilityOwnerClaimRepository.saveAndFlush(
                createClaim(owner, createFacility("강릉 중앙시장"), ClaimStatus.PENDING)
        );
        // 다른 상태는 섞이지 않아야 한다.
        entityManager.persist(createClaim(owner, createFacility("이미 승인된 매장"), ClaimStatus.APPROVED));
        entityManager.flush();
        entityManager.clear();

        Page<FacilityOwnerClaim> page = facilityOwnerClaimRepository.findByStatus(
                ClaimStatus.PENDING, PageRequest.of(0, 1)
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.getContent()).extracting(FacilityOwnerClaim::getClaimId).containsExactly(first.getClaimId());
        assertThat(page.getContent().get(0).getFacility().getName()).isEqualTo("카페 파도살롱");

        Page<FacilityOwnerClaim> secondPage = facilityOwnerClaimRepository.findByStatus(
                ClaimStatus.PENDING, PageRequest.of(1, 1)
        );
        assertThat(secondPage.getContent()).extracting(FacilityOwnerClaim::getClaimId)
                .containsExactly(second.getClaimId());
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    void findApprovedFacilityIdsIn_주어진_시설_중_승인된_것만_골라낸다() {
        Facility approvedFacility = createFacility("카페 파도살롱");
        Facility pendingFacility = createFacility("대기 매장");
        Facility unrelatedFacility = createFacility("관계없는 매장");
        entityManager.persist(createClaim(owner, approvedFacility, ClaimStatus.APPROVED));
        entityManager.persist(createClaim(owner, pendingFacility, ClaimStatus.PENDING));
        entityManager.flush();
        entityManager.clear();

        assertThat(facilityOwnerClaimRepository.findApprovedFacilityIdsIn(
                List.of(approvedFacility.getFacilityId(), pendingFacility.getFacilityId(), unrelatedFacility.getFacilityId())
        )).containsExactly(approvedFacility.getFacilityId());
    }

    @Test
    void findByIdForUpdate_존재하는_신청을_찾는다() {
        FacilityOwnerClaim saved = facilityOwnerClaimRepository.saveAndFlush(
                createClaim(owner, createFacility("카페 파도살롱"), ClaimStatus.PENDING)
        );
        entityManager.clear();

        assertThat(facilityOwnerClaimRepository.findByIdForUpdate(saved.getClaimId())).isPresent();
        assertThat(facilityOwnerClaimRepository.findByIdForUpdate(-1L)).isEmpty();
    }
}
