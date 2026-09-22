package com.freepets.domain.facility.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.freepets.domain.facility.entity.Region;

public interface RegionRepository extends JpaRepository<Region, Long> {

    /**
     * 지역 칩에 쓸 전체 목록. 행정구역 코드 순이다.
     *
     * <p>시설 수 순으로 정렬하지 않는다. 전국 목록이 고정이라 코드 순이면 리뷰가 쌓여도 칩 위치가
     * 변하지 않아, 사용자가 자기 지역이 어디쯤 있는지 기억할 수 있다.
     */
    List<Region> findAllByOrderBySidoCodeAscSigunguCodeAsc();

    /**
     * 신규 매장 등록 시 사업자가 고른 지역코드가 실재하는지 확인하는 데 쓴다. {@code sigunguCode}가
     * {@code null}이면 세종특별자치시처럼 하위 시군구가 없는 시도 행과 매칭된다 — Spring Data가 null
     * 파라미터를 {@code is null} 비교로 번역해준다.
     */
    Optional<Region> findBySidoCodeAndSigunguCode(
            String sidoCode,
            String sigunguCode
    );

    /**
     * 그 시도가 실재하는지 본다. 시군구를 비운 시도 단위 조회를 검증할 때 쓴다(#154) — 하위
     * 시군구 행이 있든(대부분) 없든(세종특별자치시처럼) 시도 자체만 확인하면 된다.
     */
    boolean existsBySidoCode(String sidoCode);

}
