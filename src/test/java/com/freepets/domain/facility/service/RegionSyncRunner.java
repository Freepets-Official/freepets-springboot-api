package com.freepets.domain.facility.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 관광공사 법정동 코드를 지역 테이블에 적재하는 실행기.
 *
 * <p>지역 목록 조회와 발자국 랭킹의 지역 칩이 이 테이블을 읽는다. 비어 있으면 칩이 하나도 안 나온다.
 *
 * <p>전체 시설 적재({@code ./gradlew facilitySync})도 같은 일을 하지만 수 시간이 걸린다.
 * 지역만 채우려면 이쪽을 쓴다 — 호출이 한두 번이라 몇 초면 끝난다.
 *
 * <pre>
 * ./gradlew regionSync
 * </pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(
        named = "region.sync",
        matches = "true",
        disabledReason = "적재 전용 실행기. ./gradlew regionSync 로 실행한다."
)
class RegionSyncRunner {

    @Autowired
    private RegionSyncService regionSyncService;

    @Test
    @DisplayName("관광공사 법정동 코드를 지역 테이블에 적재한다")
    void 관광공사_법정동_코드를_지역_테이블에_적재한다() {
        regionSyncService.syncRegions();

        System.out.println("========================================");
        System.out.println(" 지역 적재를 마쳤습니다. 상세 건수는 위 로그를 확인하세요.");
        System.out.println("========================================");
    }

}
