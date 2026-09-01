package com.freepets.global.util;

import java.math.BigDecimal;

/**
 * 좌표 간 거리 계산. 하버사인 공식을 쓴다 — 두 지점이 같을 때 구면 코사인법(acos)이 부동소수점
 * 오차로 "input is out of range"가 나는 문제를 피한다.
 *
 * <p>{@link com.freepets.domain.facility.repository.FacilityRepository}의 {@code DISTANCE_METER}
 * HQL 문자열과 같은 공식이다 — 그쪽은 쿼리 안에서만 쓸 수 있는 문자열 상수라 자바 코드에서 호출할
 * 수 없어서, 코스 조립처럼 애플리케이션 코드에서 거리 계산이 필요한 곳(스톱 재정렬, 대안 시설
 * 거리)을 위해 별도로 둔다.
 */
public final class GeoUtils {

    /** 지구 평균 반지름(m). {@code FacilityRepository.EARTH_RADIUS_METER}와 동일 값. */
    private static final double EARTH_RADIUS_METER = 6371000;

    private GeoUtils() {}

    public static double distanceMeters(
            BigDecimal lat1,
            BigDecimal lng1,
            BigDecimal lat2,
            BigDecimal lng2
    ) {
        return distanceMeters(lat1.doubleValue(), lng1.doubleValue(), lat2.doubleValue(), lng2.doubleValue());
    }

    public static double distanceMeters(
            double lat1,
            double lng1,
            double lat2,
            double lng2
    ) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLatRad = Math.toRadians(lat2 - lat1);
        double deltaLngRad = Math.toRadians(lng2 - lng1);

        double a = Math.pow(Math.sin(deltaLatRad / 2), 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.pow(Math.sin(deltaLngRad / 2), 2);

        return 2 * EARTH_RADIUS_METER * Math.asin(Math.sqrt(a));
    }
}
