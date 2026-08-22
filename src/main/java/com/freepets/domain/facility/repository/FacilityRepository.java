package com.freepets.domain.facility.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.facility.entity.Facility;

public interface FacilityRepository extends JpaRepository<Facility, Long> {

    Optional<Facility> findByContentId(String contentId);

    List<Facility> findByContentIdIn(Collection<String> contentIds);

    boolean existsByContentId(String contentId);

}
