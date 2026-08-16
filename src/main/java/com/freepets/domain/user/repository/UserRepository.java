package com.freepets.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);
}
