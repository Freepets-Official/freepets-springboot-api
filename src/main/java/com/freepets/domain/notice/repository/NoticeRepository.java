package com.freepets.domain.notice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.notice.entity.Notice;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    // 고정 공지를 맨 위로, 그 안에서는 최신순 — GET /api/v1/notices 목록 정렬 기준.
    List<Notice> findAllByOrderByIsPinnedDescCreatedAtDesc();

}
