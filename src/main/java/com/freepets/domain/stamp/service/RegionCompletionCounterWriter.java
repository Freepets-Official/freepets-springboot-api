package com.freepets.domain.stamp.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.stamp.entity.RegionCompletionCounter;
import com.freepets.domain.stamp.repository.RegionCompletionCounterRepository;

import lombok.RequiredArgsConstructor;

/**
 * 지역 완성 카운터 행이 없으면 새로 만든다. 별도 물리 트랜잭션(REQUIRES_NEW)에서 시도한다 —
 * 두 요청이 동시에 같은(아직 아무도 완성하지 않은) 지역의 카운터를 처음 만들려 하면 유니크
 * 제약 위반이 나는데, 이 앱의 기본 {@code JpaTransactionManager}는 세이브포인트(NESTED
 * 전파)를 지원하지 않아 같은 트랜잭션 안에서 그 예외를 잡아도 트랜잭션 전체가 이미 abort
 * 상태다(Postgres SQLSTATE 25P02). 그래서 독립된 트랜잭션으로 생성을 시도하고, 위반이 나면
 * (다른 요청이 먼저 만듦) 조용히 넘어간다 — 어느 쪽이든 호출부(RegionCompletionService)가
 * 그 뒤 비관적 락으로 다시 조회하면 실제로 존재하는 행을 보게 된다.
 *
 * <p>같은 클래스 안의 private 메서드 호출은 Spring 프록시를 타지 않아(self-invocation)
 * REQUIRES_NEW가 무시되므로, {@code GamificationNotificationService}처럼 별도 빈으로 분리한다.
 */
@Service
@RequiredArgsConstructor
public class RegionCompletionCounterWriter {

    private final RegionCompletionCounterRepository regionCompletionCounterRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(
            String sidoCode,
            String sigunguCode
    ) {
        if (regionCompletionCounterRepository.findBySidoCodeAndSigunguCode(sidoCode, sigunguCode).isPresent()) {
            return;
        }

        try {
            regionCompletionCounterRepository.save(
                    RegionCompletionCounter.builder()
                            .sidoCode(sidoCode)
                            .sigunguCode(sigunguCode)
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            // 동시에 다른 요청이 먼저 만들었다 — 이미 있으니 무시한다.
        }
    }

}
