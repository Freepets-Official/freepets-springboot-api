package com.freepets.domain.pet.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.pet.entity.Pet;

public interface PetRepository extends JpaRepository<Pet, Long> {

    List<Pet> findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(Long userId);

    Optional<Pet> findByPetIdAndDeletedAtIsNull(Long petId);

    List<Pet> findAllByPetIdInAndDeletedAtIsNull(List<Long> petIds);

    /**
     * 랭킹 목록의 "대표 반려동물"(가장 먼저 등록한 1마리) 배치 조회 전용 — 상위 N명의 userId를
     * 한 번에 넘기고, 호출부가 Java에서 userId별 첫 항목(petId 오름차순 = 가장 먼저 등록한 순)만
     * 골라 쓴다. userId 하나씩 N+1로 조회하지 않기 위함이다.
     */
    List<Pet> findAllByUserIdInAndDeletedAtIsNullOrderByUserIdAscPetIdAsc(List<Long> userIds);
}
