package com.freepets.domain.gamification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.config.JpaAuditingConfig;

import jakarta.persistence.EntityManager;

/**
 * {@link PetXpEventRepository#insertIgnoringDuplicate}(네이티브 INSERT ... ON CONFLICT DO
 * NOTHING) 검증. 이 저장소의 첫 네이티브 쿼리라, 목으로는 SQL 문법 자체가 실제 DB(H2, Postgres
 * 모드)에서 동작하는지 확인할 수 없다 — 특히 ON CONFLICT DO NOTHING이 H2에서도 지원되는지,
 * 유니크 제약을 실제로 건드리는지는 직접 돌려봐야 안다(XpEventRepositoryGroupedCountTest와
 * 같은 이유).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:petXpEventInsertIgnoringDuplicate;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS freepets",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class PetXpEventRepositoryTest {

    @Autowired
    private PetXpEventRepository petXpEventRepository;

    @Autowired
    private EntityManager entityManager;

    private Long petId;

    @BeforeEach
    void setUp() {
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
                .weight(BigDecimal.valueOf(3.4))
                .breedSize(BreedSize.SMALL)
                .build();
        entityManager.persist(pet);
        entityManager.flush();
        petId = pet.getPetId();
    }

    @Test
    @DisplayName("처음 지급이면 1행이 삽입된다")
    void 처음_지급이면_1행이_삽입된다() {
        int insertedRows = petXpEventRepository.insertIgnoringDuplicate(petId, XpSourceType.PETCHECK.name(), 100L, 5, null);

        assertThat(insertedRows).isEqualTo(1);
        assertThat(petXpEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 (pet, sourceType, sourceId) 조합은 두 번째부터 0행이 삽입되고 예외를 던지지 않는다")
    void 같은_조합은_두번째부터_0행이_삽입되고_예외를_던지지_않는다() {
        petXpEventRepository.insertIgnoringDuplicate(petId, XpSourceType.PETCHECK.name(), 100L, 5, null);
        entityManager.flush();

        // uk_pet_xp_events_pet_source(pet_id, source_type, source_id) 위반 — 예외 대신 0을
        // 반환해야 한다. 여기서 예외가 나면 트랜잭션이 abort돼 이후 같은 트랜잭션의 어떤
        // statement도 실패한다(Postgres SQLSTATE 25P02와 동일한 이유로, 이 테스트에서
        // GamificationService.creditPets가 여러 반려동물을 순회할 수 있어야 하는 전제가 깨진다).
        int insertedRows = petXpEventRepository.insertIgnoringDuplicate(petId, XpSourceType.PETCHECK.name(), 100L, 5, null);

        assertThat(insertedRows).isZero();
        assertThat(petXpEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 (pet, sourceType, componentSignature) 조합도 두 번째부터 0행이 삽입된다")
    void 같은_componentSignature_조합도_두번째부터_0행이_삽입된다() {
        petXpEventRepository.insertIgnoringDuplicate(petId, XpSourceType.COURSE_PUBLISHED.name(), 100L, 25, "1,2");
        entityManager.flush();

        // sourceId(코스 id)는 다르지만(101L) componentSignature가 같아
        // uk_pet_xp_events_pet_source_type_component_signature에 걸려야 한다.
        int insertedRows = petXpEventRepository.insertIgnoringDuplicate(
                petId, XpSourceType.COURSE_PUBLISHED.name(), 101L, 25, "1,2");

        assertThat(insertedRows).isZero();
        assertThat(petXpEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("componentSignature가 둘 다 null이면 서로 다른 지급으로 취급한다(NULL은 유니크 제약에서 서로 다르게 취급됨)")
    void componentSignature가_null이면_서로_다른_지급으로_취급한다() {
        petXpEventRepository.insertIgnoringDuplicate(petId, XpSourceType.PETCHECK.name(), 100L, 5, null);
        entityManager.flush();

        int insertedRows = petXpEventRepository.insertIgnoringDuplicate(petId, XpSourceType.PETCHECK.name(), 200L, 5, null);

        assertThat(insertedRows).isEqualTo(1);
        assertThat(petXpEventRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("삽입 뒤 트랜잭션이 abort되지 않아 같은 트랜잭션에서 다음 반려동물도 정상 삽입된다")
    void 중복_삽입_뒤에도_같은_트랜잭션에서_다음_삽입이_정상_동작한다() {
        // creditPets가 여러 반려동물을 한 트랜잭션 안에서 순회하는 상황을 그대로 재현한다 —
        // 첫 반려동물이 중복이어도 트랜잭션이 살아있어야 다음 반려동물이 정상 지급된다.
        User otherUser = User.builder()
                .email("owner2@test.com")
                .passwordHash("encodedPassword")
                .nickname("냥이엄마")
                .provider(Provider.LOCAL)
                .build();
        entityManager.persist(otherUser);
        Pet otherPet = Pet.builder()
                .user(otherUser)
                .name("나비")
                .kind(Kind.CAT)
                .species("코숏")
                .weight(BigDecimal.valueOf(3.4))
                .breedSize(BreedSize.SMALL)
                .build();
        entityManager.persist(otherPet);
        entityManager.flush();

        petXpEventRepository.insertIgnoringDuplicate(petId, XpSourceType.PETCHECK.name(), 100L, 5, null);
        entityManager.flush();

        int firstPetResult = petXpEventRepository.insertIgnoringDuplicate(petId, XpSourceType.PETCHECK.name(), 100L, 5, null);
        int secondPetResult = petXpEventRepository.insertIgnoringDuplicate(
                otherPet.getPetId(), XpSourceType.PETCHECK.name(), 100L, 5, null);

        assertThat(firstPetResult).isZero();
        assertThat(secondPetResult).isEqualTo(1);
        assertThat(petXpEventRepository.count()).isEqualTo(2);
    }
}
