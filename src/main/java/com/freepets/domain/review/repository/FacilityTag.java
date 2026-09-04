package com.freepets.domain.review.repository;

import com.freepets.domain.review.entity.Tag;

/**
 * (시설, 태그) 한 쌍 — {@link ReviewTagRepository#findTagsByFacilityIdIn}의 다건 결과. 후보
 * 시설 여러 곳의 태그를 한 번에 묶어 조회할 때, 시설별로 다시 그룹핑하는 데 쓴다.
 */
public record FacilityTag(
        Long facilityId,
        Tag tag
) {}
