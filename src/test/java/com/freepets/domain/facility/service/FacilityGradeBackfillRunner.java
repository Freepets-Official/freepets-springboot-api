package com.freepets.domain.facility.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 저장된 리뷰를 시설의 집계 캐시(친화도 점수·리뷰 수·발자국 등급)에 1회 반영하는 실행기.
 *
 * <p>캐시를 도입하기 전에 쌓인 리뷰는 갱신 지점을 지나지 않았으므로 시설 값이 비어 있다.
 * 그 상태로는 발자국 랭킹에 아무 시설도 뜨지 않는다.
 *
 * <p>전 시설을 훑고 DB에 쓰므로 {@code test} 태스크에서는 실행되지 않는다. 전용 태스크로만 돌린다.
 *
 * <pre>
 * ./gradlew facilityGradeBackfill
 * </pre>
 *
 * <p>{@code db/pending-manual-migrations.sql}의 {@code pet_score} 타입 변경을 먼저 적용해야 한다.
 */
@SpringBootTest
@EnabledIfSystemProperty(
        named = "facility.grade.backfill",
        matches = "true",
        disabledReason = "백필 전용 실행기. ./gradlew facilityGradeBackfill 로 실행한다."
)
class FacilityGradeBackfillRunner {

    @Autowired
    private FacilityGradeCacheService facilityGradeCacheService;

    @Test
    @DisplayName("전 시설의 발자국 등급 캐시를 다시 계산한다")
    void 전_시설의_발자국_등급_캐시를_다시_계산한다() {
        long refreshedCount = facilityGradeCacheService.refreshAll();

        System.out.println("========================================");
        System.out.println(" 발자국 등급 캐시 백필 결과");
        System.out.println("========================================");
        System.out.println(" 반영한 시설 수: " + refreshedCount);
        System.out.println("========================================");
    }

}
