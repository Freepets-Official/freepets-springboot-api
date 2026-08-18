package com.freepets.domain.pet.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.pet.entity.Pet;

public interface PetRepository extends JpaRepository<Pet, Long> {

    List<Pet> findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(Long userId);

    Optional<Pet> findByPetIdAndDeletedAtIsNull(Long petId);
}
