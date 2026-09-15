package com.freepets.domain.review.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.review.entity.ReviewHelpful;

public interface ReviewHelpfulRepository extends JpaRepository<ReviewHelpful, Long> {

    boolean existsByReviewReviewIdAndUserId(
            Long reviewId,
            Long userId
    );

    // ReviewReportRepository.findAllByUserIdAndReviewReviewIdIn과 같은 이유 — 목록 조회에서
    // "내가 도움됐어요를 누른 리뷰인지"를 리뷰 수만큼 따로 조회하지 않고 한 번에 묶어 온다.
    List<ReviewHelpful> findAllByUserIdAndReviewReviewIdIn(
            Long userId,
            List<Long> reviewIds
    );

}
