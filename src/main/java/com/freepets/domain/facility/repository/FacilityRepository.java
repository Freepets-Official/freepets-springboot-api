package com.freepets.domain.facility.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.PetConditionStatus;

public interface FacilityRepository extends JpaRepository<Facility, Long> {

    /** 도 → 라디안 변환 계수. HQL에는 radians 함수를 기대할 수 없어 직접 곱한다. */
    String RADIAN_PER_DEGREE = "0.017453292519943295";

    /** 지구 평균 반지름(m). */
    String EARTH_RADIUS_METER = "6371000";

    /**
     * 사용자 위치로부터의 거리(m).
     *
     * <p>구면 코사인법 대신 하버사인을 쓴다. 코사인법은 두 지점이 같을 때 부동소수점 오차로
     * {@code acos} 인자가 1을 넘어서 Postgres가 "input is out of range"로 실패한다.
     *
     * <p>{@code lat}/{@code lng}는 {@code numeric(10,7)}이라 삼각함수 오버로드가 모호해질 수 있어
     * 명시적으로 double로 캐스팅한다.
     */
    String DISTANCE_METER = "(2 * " + EARTH_RADIUS_METER + " * asin(sqrt("
            + "power(sin((cast(facility.lat as double) * " + RADIAN_PER_DEGREE + " - :userLatitudeRadian) / 2), 2)"
            + " + cos(:userLatitudeRadian)"
            + " * cos(cast(facility.lat as double) * " + RADIAN_PER_DEGREE + ")"
            + " * power(sin((cast(facility.lng as double) * " + RADIAN_PER_DEGREE + " - :userLongitudeRadian) / 2), 2)"
            + ")))";

    String SELECT_WITH_DISTANCE =
            "select new com.freepets.domain.facility.repository.FacilityWithDistance(facility, " + DISTANCE_METER + ") "
            + "from Facility facility ";

    String SELECT_COUNT = "select count(facility) from Facility facility ";

    /** 좌표가 없는 시설은 거리를 계산할 수 없어 제외한다. 변환기가 한반도 밖 좌표를 null로 만든다. */
    String SEARCH_FILTER =
            "where facility.isActive = true "
            + "and facility.lat is not null "
            + "and facility.lng is not null "
            + "and (:category is null or facility.category = :category) "
            + "and (:petAllowed is null or facility.petAllowed = :petAllowed) "
            + "and (:keyword is null "
            + "or lower(facility.name) like :keyword escape '!' "
            + "or lower(facility.address) like :keyword escape '!') ";

    /**
     * 반경 제한.
     *
     * <p>경계 사각형 비교가 앞에 오는 것이 핵심이다. 거리는 계산식이라
     * {@code idx_facilities_coordinate}를 쓸 수 없지만, {@code between}은 인덱스 컬럼에 대한
     * 순수 범위 비교라 인덱스 탐색이 가능하다. 전체 행이 아니라 사각형 안의 후보에 대해서만
     * 삼각함수와 정렬이 돌게 된다.
     *
     * <p>사각형은 원의 외접이라 모서리가 반경의 √2배까지 멀어지므로, 실제 거리 비교로 한 번 더 걸러낸다.
     */
    String RADIUS_FILTER =
            "and facility.lat between :minimumLatitude and :maximumLatitude "
            + "and facility.lng between :minimumLongitude and :maximumLongitude "
            + "and " + DISTANCE_METER + " <= :radiusMeter ";

    /**
     * 거리가 같은 시설이 실제로 존재하므로 ID로 순서를 고정한다.
     *
     * <p>이 보조 기준이 없으면 같은 거리끼리의 순서를 DB가 매번 다르게 정할 수 있고,
     * LIMIT/OFFSET 페이징에서 같은 시설이 두 페이지에 나오거나 아예 빠질 수 있다.
     */
    String ORDER_BY_DISTANCE = "order by " + DISTANCE_METER + ", facility.facilityId";

    Optional<Facility> findByContentId(String contentId);

    List<Facility> findByContentIdIn(Collection<String> contentIds);

    boolean existsByContentId(String contentId);

    /**
     * {@code FacilityConditionLlmBatchService}가 배치 파싱 대상을 페이지 단위로 훑는 데 쓴다.
     * 처리된 행은 상태가 바뀌어 다음 조회에서 자연히 빠지므로, 항상 {@code Pageable.ofSize(N)}
     * (0페이지)로만 호출해도 전량을 순회할 수 있다.
     */
    Slice<Facility> findByPetConditionStatus(
            PetConditionStatus petConditionStatus,
            Pageable pageable
    );

    @Query(SELECT_WITH_DISTANCE + SEARCH_FILTER + ORDER_BY_DISTANCE)
    List<FacilityWithDistance> search(
            @Param("userLatitudeRadian") double userLatitudeRadian,
            @Param("userLongitudeRadian") double userLongitudeRadian,
            @Param("keyword") String keyword,
            @Param("category") FacilityCategory category,
            @Param("petAllowed") PetAllowed petAllowed,
            Pageable pageable
    );

    @Query(SELECT_COUNT + SEARCH_FILTER)
    long countSearch(
            @Param("keyword") String keyword,
            @Param("category") FacilityCategory category,
            @Param("petAllowed") PetAllowed petAllowed
    );

    @Query(SELECT_WITH_DISTANCE + SEARCH_FILTER + RADIUS_FILTER + ORDER_BY_DISTANCE)
    List<FacilityWithDistance> searchWithinRadius(
            @Param("userLatitudeRadian") double userLatitudeRadian,
            @Param("userLongitudeRadian") double userLongitudeRadian,
            @Param("keyword") String keyword,
            @Param("category") FacilityCategory category,
            @Param("petAllowed") PetAllowed petAllowed,
            @Param("minimumLatitude") BigDecimal minimumLatitude,
            @Param("maximumLatitude") BigDecimal maximumLatitude,
            @Param("minimumLongitude") BigDecimal minimumLongitude,
            @Param("maximumLongitude") BigDecimal maximumLongitude,
            @Param("radiusMeter") double radiusMeter,
            Pageable pageable
    );

    @Query(SELECT_COUNT + SEARCH_FILTER + RADIUS_FILTER)
    long countSearchWithinRadius(
            @Param("userLatitudeRadian") double userLatitudeRadian,
            @Param("userLongitudeRadian") double userLongitudeRadian,
            @Param("keyword") String keyword,
            @Param("category") FacilityCategory category,
            @Param("petAllowed") PetAllowed petAllowed,
            @Param("minimumLatitude") BigDecimal minimumLatitude,
            @Param("maximumLatitude") BigDecimal maximumLatitude,
            @Param("minimumLongitude") BigDecimal minimumLongitude,
            @Param("maximumLongitude") BigDecimal maximumLongitude,
            @Param("radiusMeter") double radiusMeter
    );

}
