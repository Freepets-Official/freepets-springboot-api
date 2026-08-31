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
     * 상세 조회용 시설 단건 + 사용자 위치로부터의 거리.
     *
     * <p>좌표가 없는 시설은 결과에서 빠진다. {@code DISTANCE_METER}가 null이 되어
     * {@link FacilityWithDistance}의 원시 타입 {@code double}에 담기지 못하기 때문이다.
     * 그런 시설도 상세는 보여줘야 하므로, 호출부가 결과가 비었을 때 {@code findById}로 다시 집는다.
     * 즉 빈 결과는 "시설이 없다"와 "좌표가 없다" 둘 중 하나를 뜻한다.
     *
     * <p>{@code isActive} 조건은 걸지 않는다. 비표출로 내려간 시설이라도 저장해둔 링크로 들어올 수
     * 있고, 리뷰 조회 API도 활성 여부를 보지 않아 기준을 맞춘다.
     */
    @Query(SELECT_WITH_DISTANCE
            + "where facility.facilityId = :facilityId "
            + "and facility.lat is not null "
            + "and facility.lng is not null")
    Optional<FacilityWithDistance> findWithDistanceById(
            @Param("userLatitudeRadian") double userLatitudeRadian,
            @Param("userLongitudeRadian") double userLongitudeRadian,
            @Param("facilityId") Long facilityId
    );

    /**
     * {@code FacilityConditionLlmBatchService}가 배치 파싱 대상을 페이지 단위로 훑는 데 쓴다.
     * 처리된 행은 상태가 바뀌어 다음 조회에서 자연히 빠지므로, 항상 {@code Pageable.ofSize(N)}
     * (0페이지)로만 호출해도 전량을 순회할 수 있다.
     */
    Slice<Facility> findByPetConditionStatus(
            PetConditionStatus petConditionStatus,
            Pageable pageable
    );

    /**
     * {@code facilityConditionParseSample} 같은 소규모 검증 실행에서 쓴다. NOT_PROCESSED의
     * 약 90%는 조건 원문 자체가 없어 LLM을 호출하지 않고 곧장 NO_CONDITION으로 빠지므로,
     * {@code findByPetConditionStatus}로 뽑은 샘플은 파싱 결과를 검증하는 데 쓸모가 없을 수
     * 있다 — facility_id 순서에 조건없음 시설이 몰려 있으면 특히 그렇다. 이 쿼리는 조건 원문이
     * 하나라도 있는 시설만 걸러 실제로 LLM이 호출되는 케이스를 보장한다.
     */
    @Query("""
            select facility from Facility facility
            where facility.petConditionStatus = :petConditionStatus
            and (
                (facility.accompanyType is not null and length(trim(facility.accompanyType)) > 0) or
                (facility.allowedAnimalText is not null and length(trim(facility.allowedAnimalText)) > 0) or
                (facility.requiredMatterText is not null and length(trim(facility.requiredMatterText)) > 0) or
                (facility.etcAccompanyText is not null and length(trim(facility.etcAccompanyText)) > 0) or
                (facility.accidentRiskText is not null and length(trim(facility.accidentRiskText)) > 0)
            )
            """)
    Slice<Facility> findByPetConditionStatusWithConditionText(
            @Param("petConditionStatus") PetConditionStatus petConditionStatus,
            Pageable pageable
    );

    /**
     * {@code facilityConditionParseSampleWithKeyword} 검증 실행에서 쓴다. "맹견" 같은 특정
     * 키워드가 원문에 있는 시설만 골라 그 케이스에 대한 파싱 결과를 집중적으로 확인할 때
     * 쓴다 — {@code findByPetConditionStatusWithConditionText}는 조건 원문 유무만 볼 뿐
     * 어떤 조건인지는 안 가려서, 특정 시나리오를 검증하려면 이 쿼리가 필요하다.
     */
    @Query("""
            select facility from Facility facility
            where facility.petConditionStatus = :petConditionStatus
            and (
                facility.accompanyType like concat('%', :keyword, '%') or
                facility.allowedAnimalText like concat('%', :keyword, '%') or
                facility.requiredMatterText like concat('%', :keyword, '%') or
                facility.etcAccompanyText like concat('%', :keyword, '%') or
                facility.accidentRiskText like concat('%', :keyword, '%')
            )
            """)
    Slice<Facility> findByPetConditionStatusAndConditionTextContaining(
            @Param("petConditionStatus") PetConditionStatus petConditionStatus,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    /**
     * {@code facilityConditionInspectParsedWeight} 검증 실행에서 쓴다. 원문에 kg 언급이
     * 없는데 LLM이 maxWeight를 지어내는 사례(facility 4996, 5211 등)를 막는 방어 코드를
     * 넣은 뒤, 반대로 원문에 실제 체중 제한이 있는 정상 케이스는 여전히 잘 뽑히는지 실제
     * 데이터로 확인할 때 쓴다.
     */
    Slice<Facility> findByPetConditionStatusAndMaxWeightIsNotNull(
            PetConditionStatus petConditionStatus,
            Pageable pageable
    );

    /**
     * {@code FacilityConditionLlmBatchService.cleanUpMaxWeightWithoutSourceEvidence}가 이미
     * 저장된 시설 중 원문에 kg 언급 없이 maxWeight만 채워진 과거 오염 데이터를 찾을 때 쓴다.
     * petConditionStatus를 안 가려서 PARSED/AMBIGUOUS 어느 쪽이든 다 걸린다 — 상태가 뭐든
     * 이미 재파싱 대상에서 빠진 시설이 청소 대상이기 때문이다.
     */
    Slice<Facility> findByMaxWeightIsNotNull(Pageable pageable);

    /**
     * {@code FacilityConditionLlmBatchApiService}(#39)가 Batch API 제출 대상을 고를 때 쓴다.
     * {@code FacilityConditionLlmParser.parse()}가 실제로 LLM을 호출하는 조건과 정확히
     * 일치시켰다 — {@code petAllowed=DENIED}는 애초에 호출 안 하고(resolve() 참고),
     * {@code accompanyType}은 실질 조건 문장이 없는 코드성 필드라 나머지 4종만 본다(그
     * 필드만 있으면 파서가 LLM 호출 없이 기계적으로 처리한다). 이 조건과 어긋나면 제출
     * 대상 수와 실제 호출 대상 수가 갈린다.
     */
    @Query("""
            select facility from Facility facility
            where facility.petConditionStatus = :petConditionStatus
            and facility.petAllowed <> com.freepets.domain.facility.entity.PetAllowed.DENIED
            and (
                (facility.allowedAnimalText is not null and length(trim(facility.allowedAnimalText)) > 0) or
                (facility.requiredMatterText is not null and length(trim(facility.requiredMatterText)) > 0) or
                (facility.etcAccompanyText is not null and length(trim(facility.etcAccompanyText)) > 0) or
                (facility.accidentRiskText is not null and length(trim(facility.accidentRiskText)) > 0)
            )
            """)
    Slice<Facility> findRequiringLlmParse(
            @Param("petConditionStatus") PetConditionStatus petConditionStatus,
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
