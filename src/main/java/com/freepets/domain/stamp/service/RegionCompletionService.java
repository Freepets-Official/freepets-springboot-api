package com.freepets.domain.stamp.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.stamp.entity.RegionCompletion;
import com.freepets.domain.stamp.entity.RegionCompletionCounter;
import com.freepets.domain.stamp.repository.RegionCompletionCounterRepository;
import com.freepets.domain.stamp.repository.RegionCompletionRepository;
import com.freepets.domain.user.entity.User;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

/**
 * "○○시 n번째 등반자" — 이 사용자가 그 시/군/구를 처음 완성한 순번을 원자적으로 확정해 저장한다
 * ({@code freepets-docs/docs/13-지역-랭킹.md} 2절). {@code StampCommandService}가 이번 도장이
 * 그 지역의 첫 도장임을 이미 확인한 뒤에만 부른다 — 이 서비스는 "완성했다"는 판단은 하지 않고
 * 순번 부여만 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RegionCompletionService {

    private final RegionCompletionCounterRepository regionCompletionCounterRepository;
    private final RegionCompletionRepository regionCompletionRepository;
    private final RegionCompletionCounterWriter regionCompletionCounterWriter;

    public RegionCompletion completeRegion(
            User user,
            String sidoCode,
            String sigunguCode
    ) {
        regionCompletionCounterWriter.ensureExists(sidoCode, sigunguCode);

        // 여기서부터 이 지역의 카운터 행을 잠가, 같은 지역을 동시에 처음 완성하는 다른 요청과
        // 직렬화한다 — ensureExists가 커밋한 뒤라 이 조회는 항상 행을 찾는다.
        RegionCompletionCounter counter = regionCompletionCounterRepository.findBySidoCodeAndSigunguCode(sidoCode, sigunguCode)
                .orElseThrow(() -> new GeneralException(ErrorStatus.COMMON500));
        long completionOrder = counter.incrementAndGet();

        return regionCompletionRepository.save(
                RegionCompletion.builder()
                        .user(user)
                        .sidoCode(sidoCode)
                        .sigunguCode(sigunguCode)
                        .completionOrder(completionOrder)
                        .completedAt(LocalDateTime.now())
                        .build()
        );
    }

}
