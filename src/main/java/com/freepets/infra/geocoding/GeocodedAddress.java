package com.freepets.infra.geocoding;

import java.math.BigDecimal;

/**
 * 지오코딩 결과 좌표. {@code Facility.lat}/{@code lng}와 같은 정밀도(precision=10, scale=7)를 따른다.
 */
public record GeocodedAddress(
        BigDecimal lat,
        BigDecimal lng
) {
}
