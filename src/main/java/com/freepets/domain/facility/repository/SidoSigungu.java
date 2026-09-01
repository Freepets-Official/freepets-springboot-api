package com.freepets.domain.facility.repository;

/**
 * 실제 동반 가능 시설이 있는 지역 조합 하나. {@code GET /api/v1/courses/regions}가 프론트
 * 지역 선택 드롭다운을 채울 때 쓴다 — 자유텍스트 입력을 받으면 "강원"처럼 실제 저장된 값
 * ("강원특별자치도")과 다른 표기를 보내 후보가 0건이 되는 문제가 있어서, 실제 존재하는 값만
 * 고르게 한다.
 */
public interface SidoSigungu {

    String getSido();

    /** 시/도 전체를 아우르는 경우 등 값이 없을 수 있다. */
    String getSigungu();

}
