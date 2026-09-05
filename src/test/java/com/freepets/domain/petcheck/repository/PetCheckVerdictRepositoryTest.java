package com.freepets.domain.petcheck.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;

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
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.petcheck.entity.PetCheck;
import com.freepets.domain.petcheck.entity.PetCheckResult;
import com.freepets.domain.petcheck.entity.PetCheckVerdict;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

import com.freepets.global.config.JpaAuditingConfig;

import jakarta.persistence.EntityManager;

/**
 * GET /verify/{code}의 JOIN FETCH 검증.
 *
 * <p>목으로는 지연 로딩 여부를 확인할 수 없어(목은 애초에 프록시가 아니라 즉시 값을 준다)
 * 실제 DB에 저장하고 영속성 컨텍스트를 비운 뒤 조회해야 의미가 있다. {@code entityManager.clear()}
 * 없이 조회하면 1차 캐시에서 바로 채워져 JOIN FETCH가 빠져도 테스트가 통과해버린다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:petCheckVerdict;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS freepets",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PetCheckVerdictRepositoryTest {

    @Autowired
    private PetCheckVerdictRepository petCheckVerdictRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 검증_코드로_조회하면_영속성_컨텍스트를_비워도_시설과_반려동물까지_지연로딩_예외_없이_읽힌다() {
        Facility facility = Facility.builder()
                .name("테라로자 커피공장")
                .category(FacilityCategory.CAFE)
                .address("경남 하동군")
                .petAllowed(PetAllowed.ALLOWED)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
        entityManager.persist(facility);

        User user = User.builder()
                .email("owner@test.com")
                .passwordHash("encodedPassword")
                .nickname("몽이아빠")
                .provider(Provider.LOCAL)
                .build();
        entityManager.persist(user);

        Pet pet = Pet.builder()
                .user(user)
                .name("몽이")
                .kind(Kind.DOG)
                .species("말티즈")
                .weight(new BigDecimal("3.20"))
                .breedSize(BreedSize.SMALL)
                .isVaccinated(true)
                .build();
        entityManager.persist(pet);

        PetCheck petCheck = PetCheck.builder()
                .user(user)
                .facility(facility)
                .overall(PetCheckResult.CONDITIONAL)
                .build();
        PetCheckVerdict verdict = PetCheckVerdict.builder()
                .pet(pet)
                .result(PetCheckResult.CONDITIONAL)
                .reason("몽이는 리드줄만 착용하면 이용 가능합니다")
                .conditions("[\"리드줄 필수 착용\"]")
                .verifyCode("FP-ABCDEF123456")
                .build();
        petCheck.addVerdict(verdict);
        entityManager.persist(petCheck);

        entityManager.flush();
        entityManager.clear();

        assertThatCode(() -> {
            PetCheckVerdict found = petCheckVerdictRepository.findByVerifyCode("FP-ABCDEF123456").orElseThrow();

            // clear() 뒤라 이 접근들이 지연 로딩을 트리거한다 — JOIN FETCH가 빠지면
            // LazyInitializationException(영속성 컨텍스트/세션 종료)으로 여기서 실패한다.
            assertThat(found.getPetCheck().getFacility().getName()).isEqualTo("테라로자 커피공장");
            assertThat(found.getPet().getName()).isEqualTo("몽이");
        }).doesNotThrowAnyException();
    }

    @Test
    void 반려동물이_삭제돼_pet이_null이어도_조회는_된다() {
        Facility facility = Facility.builder()
                .name("테라로자 커피공장")
                .category(FacilityCategory.CAFE)
                .address("경남 하동군")
                .petAllowed(PetAllowed.ALLOWED)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
        entityManager.persist(facility);

        User user = User.builder()
                .email("owner2@test.com")
                .passwordHash("encodedPassword")
                .nickname("보리엄마")
                .provider(Provider.LOCAL)
                .build();
        entityManager.persist(user);

        PetCheck petCheck = PetCheck.builder()
                .user(user)
                .facility(facility)
                .overall(PetCheckResult.ALLOWED)
                .build();
        // pet(null) — fk_verdict_pet ON DELETE SET NULL로 반려동물 삭제 후 남는 모습을 그대로 재현.
        PetCheckVerdict verdict = PetCheckVerdict.builder()
                .result(PetCheckResult.ALLOWED)
                .reason("모든 조건을 충족해 출입 가능합니다")
                .conditions("[]")
                .verifyCode("FP-GHIJKL654321")
                .build();
        petCheck.addVerdict(verdict);
        entityManager.persist(petCheck);

        entityManager.flush();
        entityManager.clear();

        PetCheckVerdict found = petCheckVerdictRepository.findByVerifyCode("FP-GHIJKL654321").orElseThrow();

        assertThat(found.getPet()).isNull();
        assertThat(found.getPetCheck().getFacility().getName()).isEqualTo("테라로자 커피공장");
    }

    @Test
    void verify_code가_null인_레거시_행이_여러_개여도_유니크_제약에_안_걸린다() {
        // PetCheckVerdict의 verify_code 컬럼 주석에 적힌 전제("nullable로 두고 백필하지 않는다")가
        // 실제로 안전한지 확인한다. PostgreSQL(과 그 호환 모드인 H2)은 UNIQUE 제약에서
        // NULL끼리는 서로 다른 값으로 취급하므로 여러 행이 NULL을 가져도 위반이 아니어야 한다 —
        // 이 가정이 틀렸다면 이 기능을 넣는 순간 기존 판별 기록 저장이 전부 깨진다.
        Facility facility = Facility.builder()
                .name("테라로자 커피공장")
                .category(FacilityCategory.CAFE)
                .address("경남 하동군")
                .petAllowed(PetAllowed.ALLOWED)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
        entityManager.persist(facility);

        User user = User.builder()
                .email("owner3@test.com")
                .passwordHash("encodedPassword")
                .nickname("초코아빠")
                .provider(Provider.LOCAL)
                .build();
        entityManager.persist(user);

        PetCheck petCheck = PetCheck.builder()
                .user(user)
                .facility(facility)
                .overall(PetCheckResult.ALLOWED)
                .build();
        // verifyCode를 아예 지정하지 않은 두 verdict — 이 기능 도입 전에 쌓인 레거시 행을 재현한다.
        PetCheckVerdict legacyVerdict1 = PetCheckVerdict.builder()
                .result(PetCheckResult.ALLOWED)
                .reason("모든 조건을 충족해 출입 가능합니다")
                .conditions("[]")
                .build();
        PetCheckVerdict legacyVerdict2 = PetCheckVerdict.builder()
                .result(PetCheckResult.ALLOWED)
                .reason("모든 조건을 충족해 출입 가능합니다")
                .conditions("[]")
                .build();
        petCheck.addVerdict(legacyVerdict1);
        petCheck.addVerdict(legacyVerdict2);

        assertThatCode(() -> {
            entityManager.persist(petCheck);
            entityManager.flush();
        }).doesNotThrowAnyException();
    }
}
