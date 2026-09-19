package com.freepets.domain.gamification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.gamification.entity.PetXpEvent;

public interface PetXpEventRepository extends JpaRepository<PetXpEvent, Long> {
}
