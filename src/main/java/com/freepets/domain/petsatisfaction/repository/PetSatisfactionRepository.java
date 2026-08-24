package com.freepets.domain.petsatisfaction.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;

public interface PetSatisfactionRepository extends JpaRepository<PetSatisfaction, Long> {

    Optional<PetSatisfaction> findByPetPetIdAndFacilityFacilityId(
            Long petId,
            Long facilityId
    );
}
