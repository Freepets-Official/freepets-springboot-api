package com.freepets.domain.gamification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.gamification.entity.Badge;
import com.freepets.domain.gamification.entity.UserBadge;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {

    boolean existsByUser_IdAndBadge(
            Long userId,
            Badge badge
    );

    // GET /me/gamification — 내 배지 목록. 개수가 (콘텐츠 확장돼도) 한 유저당 아주 많아지진
    // 않을 카탈로그라 페이지네이션 없이 전체를 내려준다.
    List<UserBadge> findAllByUser_Id(Long userId);

}
