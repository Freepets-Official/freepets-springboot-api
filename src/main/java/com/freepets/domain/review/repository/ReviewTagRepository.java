package com.freepets.domain.review.repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.review.entity.ReviewTag;
import com.freepets.domain.review.entity.Tag;

public interface ReviewTagRepository extends JpaRepository<ReviewTag, Long> {

    /**
     * "취향 비슷한 새곳"(similar) 취향 프로필 산출용 — 좋아한 시설들의 리뷰에 달린 태그 집합.
     * 특정 리뷰어(이 유저)만이 아니라 그 시설에 달린 리뷰 전체의 태그를 본다 — "이 시설이 대체로
     * 어떤 곳인지"가 취향 프로필의 재료이기 때문.
     */
    @Query("""
            select distinct reviewTag.tag from ReviewTag reviewTag
            where reviewTag.review.facility.facilityId in :facilityIds
            and reviewTag.review.deletedAt is null
            """)
    Set<Tag> findDistinctTagsByFacilityIdIn(@Param("facilityIds") Collection<Long> facilityIds);

    /** 후보 시설 하나의 태그 집합(겹침 수 계산용). */
    @Query("""
            select reviewTag.tag from ReviewTag reviewTag
            where reviewTag.review.facility.facilityId = :facilityId
            and reviewTag.review.deletedAt is null
            """)
    List<Tag> findTagsByFacilityId(@Param("facilityId") Long facilityId);

    /** 이 태그들 중 하나라도 달린 리뷰가 있는 시설(단, 이미 방문한 곳은 제외) — 태그 기반 후보 풀. */
    @Query("""
            select distinct reviewTag.review.facility.facilityId from ReviewTag reviewTag
            where reviewTag.tag in :tags
            and reviewTag.review.deletedAt is null
            and reviewTag.review.facility.facilityId not in :excludedFacilityIds
            """)
    List<Long> findFacilityIdsByTagInExcluding(
            @Param("tags") Collection<Tag> tags,
            @Param("excludedFacilityIds") Collection<Long> excludedFacilityIds
    );

}
