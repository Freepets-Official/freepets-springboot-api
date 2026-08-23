package com.freepets.domain.facility.service;

import java.math.BigDecimal;

/**
 * 반경을 감싸는 경계 사각형.
 *
 * <p>거리 정렬만으로는 좌표 인덱스를 쓸 수 없다. 거리는 사용자 위치마다 달라지는 계산식이라
 * 인덱스에 미리 담아둘 수 없기 때문이다. 반면 이 사각형은 {@code lat}/{@code lng}에 대한
 * 단순 범위 비교라 {@code idx_facilities_coordinate}로 탐색된다.
 *
 * <p>따라서 사각형으로 후보를 먼저 줄이고, 그 안에서만 실제 거리를 계산한다.
 * 사각형은 원의 외접이므로 모서리는 반경의 √2배까지 멀어진다. 정확한 거리 비교가 뒤따라야 한다.
 */
public record BoundingBox(
        BigDecimal minimumLatitude,
        BigDecimal maximumLatitude,
        BigDecimal minimumLongitude,
        BigDecimal maximumLongitude
) {

    /** 위도 1도의 거리(m). 위도에 상관없이 거의 일정하다. */
    private static final double METER_PER_LATITUDE_DEGREE = 111_320.0;

    /** 경도 1도의 거리는 고위도로 갈수록 짧아진다. 극지방에서 폭이 발산하지 않도록 하한을 둔다. */
    private static final double MINIMUM_COSINE = 0.01;

    public static BoundingBox around(
            double latitude,
            double longitude,
            int radiusMeter
    ) {
        double latitudeDelta = radiusMeter / METER_PER_LATITUDE_DEGREE;
        double cosine = Math.max(Math.cos(Math.toRadians(latitude)), MINIMUM_COSINE);
        double longitudeDelta = radiusMeter / (METER_PER_LATITUDE_DEGREE * cosine);

        return new BoundingBox(
                BigDecimal.valueOf(Math.max(latitude - latitudeDelta, -90.0)),
                BigDecimal.valueOf(Math.min(latitude + latitudeDelta, 90.0)),
                BigDecimal.valueOf(Math.max(longitude - longitudeDelta, -180.0)),
                BigDecimal.valueOf(Math.min(longitude + longitudeDelta, 180.0))
        );
    }

}
