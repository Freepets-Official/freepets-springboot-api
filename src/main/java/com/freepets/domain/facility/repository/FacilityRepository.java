package com.freepets.domain.facility.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.facility.entity.Facility;

public interface FacilityRepository extends JpaRepository<Facility, Long> {
}
