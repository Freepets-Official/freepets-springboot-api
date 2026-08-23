package com.freepets.domain.petcheck.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.petcheck.entity.PetCheck;

public interface PetCheckRepository extends JpaRepository<PetCheck, Long> {

    boolean existsByPetPetIdAndFacilityFacilityId(
            Long petId,
            Long facilityId
    );
}
