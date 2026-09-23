package com.freepets.domain.review.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.review.entity.ReviewHelpful;

public interface ReviewHelpfulRepository extends JpaRepository<ReviewHelpful, Long> {

    boolean existsByReviewReviewIdAndUserId(
            Long reviewId,
            Long userId
    );

    /**
     * 도움됐어요 취소 — 지워진 행 수를 그대로 반환한다. 서비스가 이 값으로 "실제로 표시돼
     * 있었는지"를 판단해, 표시된 적 없는 걸 취소하려는 멱등 호출이면 Review.helpfulCount는
     * 건드리지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from ReviewHelpful reviewHelpful where reviewHelpful.review.reviewId = :reviewId and reviewHelpful.user.id = :userId")
    int deleteByReviewReviewIdAndUserId(
            @Param("reviewId") Long reviewId,
            @Param("userId") Long userId
    );

    // ReviewReportRepository.findAllByUserIdAndReviewReviewIdIn과 같은 이유 — 목록 조회에서
    // "내가 도움됐어요를 누른 리뷰인지"를 리뷰 수만큼 따로 조회하지 않고 한 번에 묶어 온다.
    List<ReviewHelpful> findAllByUserIdAndReviewReviewIdIn(
            Long userId,
            List<Long> reviewIds
    );

}
