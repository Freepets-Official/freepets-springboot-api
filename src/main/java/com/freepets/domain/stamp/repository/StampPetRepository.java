package com.freepets.domain.stamp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.stamp.entity.StampPet;

public interface StampPetRepository extends JpaRepository<StampPet, Long> {

    // "함께한 발자국"(반려동물별 도장 참여 횟수) 계산용 — freepets-docs PR #49.
    long countByPetPetId(Long petId);

}
