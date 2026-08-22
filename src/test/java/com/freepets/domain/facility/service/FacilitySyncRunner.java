package com.freepets.domain.facility.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 관광공사 시설 데이터를 실제로 적재하는 실행기.
 *
 * <p>외부 API를 수백 회 호출하고 DB에 수만 건을 쓰므로 {@code test} 태스크에서는 실행되지 않는다.
 * 전용 태스크로만 돌린다.
 *
 * <pre>
 * ./gradlew facilitySync
 * </pre>
 *
 * <p>인증키는 {@code application.yml}의 {@code tour-api.service-key}에서 읽는다.
 */
@SpringBootTest
@EnabledIfSystemProperty(
        named = "facility.sync",
        matches = "true",
        disabledReason = "적재 전용 실행기. ./gradlew facilitySync 로 실행한다."
)
class FacilitySyncRunner {

    @Autowired
    private FacilitySyncService facilitySyncService;

    @Test
    @DisplayName("관광공사 전체 시설을 적재한다")
    void 관광공사_전체_시설을_적재한다() {
        FacilitySyncResult result = facilitySyncService.syncAll();

        System.out.println("========================================");
        System.out.println(" 적재 결과");
        System.out.println("========================================");
        System.out.println(result.summary());
        System.out.println("========================================");
    }

}
