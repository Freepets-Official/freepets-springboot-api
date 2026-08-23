package com.freepets.domain.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 경계 사각형 검증.
 *
 * <p>이 사각형은 반경을 감싸는 외접 사각형이어야 한다. 반경보다 좁으면 실제로 반경 안에 있는
 * 시설이 조회에서 빠지므로, 넉넉한 쪽으로 틀리는 것은 괜찮지만 좁은 쪽으로 틀리면 안 된다.
 */
class BoundingBoxTest {

    private static final double SEOUL_LATITUDE = 37.5665;
    private static final double SEOUL_LONGITUDE = 126.9780;

    @Test
    @DisplayName("사각형은 사용자 위치를 중심으로 대칭이다")
    void 사각형은_사용자_위치를_중심으로_대칭이다() {
        BoundingBox boundingBox = BoundingBox.around(SEOUL_LATITUDE, SEOUL_LONGITUDE, 3000);

        double latitudeCenter = (boundingBox.minimumLatitude().doubleValue()
                + boundingBox.maximumLatitude().doubleValue()) / 2;
        double longitudeCenter = (boundingBox.minimumLongitude().doubleValue()
                + boundingBox.maximumLongitude().doubleValue()) / 2;

        assertThat(latitudeCenter).isCloseTo(SEOUL_LATITUDE, offset(1e-9));
        assertThat(longitudeCenter).isCloseTo(SEOUL_LONGITUDE, offset(1e-9));
    }

    @Test
    @DisplayName("반경 안의 지점은 사각형 안에 반드시 들어온다")
    void 반경_안의_지점은_사각형_안에_반드시_들어온다() {
        int radiusMeter = 3000;
        BoundingBox boundingBox = BoundingBox.around(SEOUL_LATITUDE, SEOUL_LONGITUDE, radiusMeter);

        // 정북·정동으로 반경만큼 떨어진 지점. 사각형 경계와 맞닿는 가장 빡빡한 경우다.
        double northLatitude = SEOUL_LATITUDE + radiusMeter / 111_320.0;
        double eastLongitude = SEOUL_LONGITUDE
                + radiusMeter / (111_320.0 * Math.cos(Math.toRadians(SEOUL_LATITUDE)));

        assertThat(boundingBox.maximumLatitude().doubleValue()).isGreaterThanOrEqualTo(northLatitude);
        assertThat(boundingBox.maximumLongitude().doubleValue()).isGreaterThanOrEqualTo(eastLongitude);
    }

    @Test
    @DisplayName("고위도로 갈수록 경도 폭이 위도 폭보다 넓어진다")
    void 고위도로_갈수록_경도_폭이_위도_폭보다_넓어진다() {
        BoundingBox boundingBox = BoundingBox.around(SEOUL_LATITUDE, SEOUL_LONGITUDE, 3000);

        double latitudeWidth = boundingBox.maximumLatitude().doubleValue()
                - boundingBox.minimumLatitude().doubleValue();
        double longitudeWidth = boundingBox.maximumLongitude().doubleValue()
                - boundingBox.minimumLongitude().doubleValue();

        assertThat(longitudeWidth).isGreaterThan(latitudeWidth);
    }

    @Test
    @DisplayName("반경이 커지면 사각형도 비례해서 커진다")
    void 반경이_커지면_사각형도_비례해서_커진다() {
        BoundingBox small = BoundingBox.around(SEOUL_LATITUDE, SEOUL_LONGITUDE, 1000);
        BoundingBox large = BoundingBox.around(SEOUL_LATITUDE, SEOUL_LONGITUDE, 10000);

        double smallWidth = small.maximumLatitude().doubleValue() - small.minimumLatitude().doubleValue();
        double largeWidth = large.maximumLatitude().doubleValue() - large.minimumLatitude().doubleValue();

        assertThat(largeWidth / smallWidth).isCloseTo(10.0, offset(1e-6));
    }

    @Test
    @DisplayName("극지방에서도 위도 경도 범위를 벗어나지 않는다")
    void 극지방에서도_위도_경도_범위를_벗어나지_않는다() {
        BoundingBox boundingBox = BoundingBox.around(89.9, 179.9, 100000);

        assertThat(boundingBox.maximumLatitude().doubleValue()).isLessThanOrEqualTo(90.0);
        assertThat(boundingBox.minimumLatitude().doubleValue()).isGreaterThanOrEqualTo(-90.0);
        assertThat(boundingBox.maximumLongitude().doubleValue()).isLessThanOrEqualTo(180.0);
        assertThat(boundingBox.minimumLongitude().doubleValue()).isGreaterThanOrEqualTo(-180.0);
    }
}
