package com.freepets.domain.review.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findAllByFacilityFacilityId(Long facilityId);

    Optional<Review> findByFacilityFacilityIdAndUserId(
            Long facilityId,
            Long userId
    );
}
