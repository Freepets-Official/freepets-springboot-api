package com.freepets.domain.review.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.review.entity.Review;
import com.freepets.domain.review.entity.ReviewHelpful;
import com.freepets.domain.review.repository.ReviewHelpfulRepository;
import com.freepets.domain.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * "도움됐어요" 표시 저장만 별도 트랜잭션에 격리한다.
 *
 * <p>PostgreSQL은 트랜잭션 안에서 문장 하나가 실패하면 그 트랜잭션 전체가 즉시 "aborted"
 * 상태가 된다. 호출부(ReviewCommandService)가 유니크 제약 위반을 잡고 같은 트랜잭션에서 계속
 * 진행해도 원래 성공했어야 할 나머지 흐름은 무사히 끝나는 것처럼 보이지만, 실제로는 JPA가 그
 * 트랜잭션을 이미 롤백 전용으로 표시해둬서 커밋 시점에 {@code RollbackException}이 뒤늦게
 * 터진다 — "이미 표시된 건 조용히 넘어간다"는 의도와 반대로 매번 500이 나게 된다.
 *
 * <p>{@code REQUIRES_NEW}로 저장만 독립된 트랜잭션에 넣으면 실패해도 그 트랜잭션만 롤백되고,
 * 호출부(리뷰 조회 등)의 트랜잭션은 전혀 영향받지 않는다. 이 메소드 안에서 예외를 잡지 않고
 * 그대로 던지는 게 핵심이다 — 그래야 스프링이 이 트랜잭션을 (추가 예외 없이) 정상적으로
 * 롤백해준다. 여기서 잡아 삼키면 스프링이 이 트랜잭션도 커밋을 시도하다 위와 같은 문제가
 * 똑같이 재현된다. 예외 처리(이미 표시된 건지 판단)는 호출부가, 이미 정상인 자기 트랜잭션
 * 안에서 한다.
 */
@Component
@RequiredArgsConstructor
public class ReviewHelpfulRecorder {

    private final ReviewHelpfulRepository reviewHelpfulRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Review review,
            User user
    ) {
        reviewHelpfulRepository.save(ReviewHelpful.builder().review(review).user(user).build());
    }

}
