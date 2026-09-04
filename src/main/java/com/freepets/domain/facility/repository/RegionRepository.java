package com.freepets.domain.facility.repository;

import java.util.List;

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

}
