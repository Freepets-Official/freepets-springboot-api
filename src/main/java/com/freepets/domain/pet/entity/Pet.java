package com.freepets.domain.pet.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.freepets.domain.petcheck.entity.PetCheck;
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

@Getter
@Entity
@Table(name = "pets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @OneToMany(mappedBy = "pet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PetCheck> petChecks = new ArrayList<>();

    @OneToMany(mappedBy = "pet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PetSatisfaction> petSatisfactions = new ArrayList<>();

    @Builder
    private Pet(
            User user,
            String name,
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
            String species,
            BigDecimal weight,
            BreedSize breedSize,
            String profile,
            LocalDate vaccinationDate,
            LocalDate nextVaccinationDate,
            boolean isVaccinated
    ) {
        this.name = name;
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

}
