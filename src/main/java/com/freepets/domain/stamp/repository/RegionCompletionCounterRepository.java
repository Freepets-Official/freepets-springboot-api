package com.freepets.domain.stamp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import com.freepets.domain.stamp.entity.RegionCompletionCounter;

public interface RegionCompletionCounterRepository extends JpaRepository<RegionCompletionCounter, Long> {

    /**
     * {@code RegionCompletionService} 전용 — 같은 지역을 동시에 처음 완성하는 두 요청을 행
     * 단위로 직렬화한다({@code UserRepository.findByIdForUpdate}와 같은 이유). 카운터 행이
     * 아직 없으면(그 지역 최초 완성) 잠글 대상이 없어 이 메서드는 빈 값을 돌려주고, 호출부가
     * 새로 만들어 저장한 뒤 다시 이 메서드로 잠근다.
     *
     * <p>파생 쿼리 메서드로 둔다(직접 쓴 {@code @Query}가 아니다) — {@code sigunguCode}는
     * 세종특별자치시처럼 하위 시군구가 없는 시도에서 실제로 {@code null}일 수 있는데
     * ({@code RegionRepository.findBySidoCodeAndSigunguCode}와 같은 케이스), 파생 쿼리는 Spring
     * Data가 null 파라미터를 자동으로 {@code is null} 비교로 바꿔준다. 직접 쓴 JPQL(예:
     * {@code c.sigunguCode = :sigunguCode})은 이 변환을 받지 못해 파라미터가 null이면 그 어떤
     * 행과도 매칭되지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RegionCompletionCounter> findBySidoCodeAndSigunguCode(
            String sidoCode,
            String sigunguCode
    );

}
