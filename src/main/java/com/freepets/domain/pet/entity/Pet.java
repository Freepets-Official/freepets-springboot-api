package com.freepets.domain.pet.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.freepets.domain.petcheck.entity.PetCheckVerdict;
import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;
import com.freepets.domain.user.entity.User;
import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;

// @BatchSize: 리뷰 목록처럼 여러 Pet 지연 프록시가 한 세션에서 동시에 초기화될 때
// 건별 쿼리 대신 IN절로 묶어서 가져오게 한다 (다른 도메인 조회 방식엔 영향 없음).
@Getter
@Entity
@Table(name = "pets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@BatchSize(size = 100)
public class Pet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pet_id")
    private Long petId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 50, nullable = false)
    private String name;

    // 기존 데이터가 있는 상태로 컬럼이 추가돼도 깨지지 않도록 기본값을 둔다.
    // 과거 데이터는 실제 종류와 다를 수 있으니 배포 후 검토가 필요하다.
    @ColumnDefault("'DOG'")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(length = 100, nullable = false)
    private String species;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "breed_size", nullable = false)
    private BreedSize breedSize;

    @Column(columnDefinition = "TEXT")
    private String profile;

    @Column(name = "vaccination_date")
    private LocalDate vaccinationDate;

    @Column(name = "next_vaccination_date")
    private LocalDate nextVaccinationDate;

    @Column(name = "is_vaccinated", nullable = false)
    private boolean isVaccinated;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 판별 세션(PetCheck) 자체가 아니라 그 안의 아이별 결과(PetCheckVerdict)에 걸린다.
    // 펫이 삭제되면 orphanRemoval 없이 verdict.pet만 NULL로 남는다(fk_verdict_pet ON DELETE SET NULL,
    // db/schema.sql) — 판별 기록 자체는 유지해야 하므로 cascade/orphanRemoval을 걸지 않는다.
    @OneToMany(mappedBy = "pet")
    private List<PetCheckVerdict> petCheckVerdicts = new ArrayList<>();

    @OneToMany(mappedBy = "pet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PetSatisfaction> petSatisfactions = new ArrayList<>();

    @Builder
    private Pet(
            User user,
            String name,
            Kind kind,
            String species,
            BigDecimal weight,
            BreedSize breedSize,
            String profile,
            LocalDate vaccinationDate,
            LocalDate nextVaccinationDate,
            boolean isVaccinated
    ) {
        this.user = user;
        this.name = name;
        this.kind = kind;
        this.species = species;
        this.weight = weight;
        this.breedSize = breedSize;
        this.profile = profile;
        this.vaccinationDate = vaccinationDate;
        this.nextVaccinationDate = nextVaccinationDate;
        this.isVaccinated = isVaccinated;
    }

    public void update(
            String name,
            Kind kind,
            String species,
            BigDecimal weight,
            BreedSize breedSize,
            String profile,
            LocalDate vaccinationDate,
            LocalDate nextVaccinationDate,
            boolean isVaccinated
    ) {
        this.name = name;
        this.kind = kind;
        this.species = species;
        this.weight = weight;
        this.breedSize = breedSize;
        this.profile = profile;
        this.vaccinationDate = vaccinationDate;
        this.nextVaccinationDate = nextVaccinationDate;
        this.isVaccinated = isVaccinated;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isOwnedBy(Long userId) {
        return user != null && user.getId().equals(userId);
    }

    /** 동물보호법 시행규칙상 맹견 품종 여부. {@link DangerousDogBreed} 참고. */
    public boolean isDangerousBreed() {
        return DangerousDogBreed.matches(species);
    }

}
