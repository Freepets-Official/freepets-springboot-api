package com.freepets.domain.petcheck.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.petcheck.entity.PetCheckVerdict;

public interface PetCheckVerdictRepository extends JpaRepository<PetCheckVerdict, Long> {
}
