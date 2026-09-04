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
import com.freepets.domain.facility.entity.PetFriendlyGrade;
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

    /**
     * 발자국 랭킹 대상 조건.
     *
     * <p>등급을 받은 시설만 노출한다(기능명세서 F3-3). 등급 판정은 리뷰가 바뀔 때 미리 계산해
     * {@code pawGradeLevel}에 저장해두므로, 여기서는 임계값을 다시 따지지 않는다. 등급 기준을
     * SQL에도 적어두면 {@link com.freepets.domain.facility.entity.PetFriendlyGrade}가 바뀔 때
     * 한쪽만 고치게 된다.
     *
     * <p>지역은 이름이 아니라 코드로 거른다. 지명 개편(강원도 → 강원특별자치도)이 있어도 코드는
     * 그대로이고, {@code idx_facilities_region}도 코드 기준이다.
     */
    String RANKING_FILTER =
            "where facility.isActive = true "
            + "and facility.pawGradeLevel > " + PetFriendlyGrade.NO_GRADE_LEVEL + " "
            + "and (:category is null or facility.category = :category) "
            + "and (:petAllowed is null or facility.petAllowed = :petAllowed) "
            + "and (:sidoCode is null or facility.sidoCode = :sidoCode) "
            + "and (:sigunguCode is null or facility.sigunguCode = :sigunguCode) ";

    /** 거리를 함께 계산할 때만 붙이는 조건. 좌표가 없는 시설은 거리를 낼 수 없어 제외된다. */
    String COORDINATE_NOT_NULL_FILTER =
            "and facility.lat is not null "
            + "and facility.lng is not null ";

    /**
     * 랭킹 정렬. 등급 내림차순 → 친화도 점수 내림차순이며, 동률은 ID로 고정한다.
     *
     * <p>점수만으로 정렬하면 안 된다. 95점·리뷰 20건은 1등급, 85점·리뷰 100건은 3등급이라
     * 뒤쪽이 위에 와야 하기 때문이다.
     *
     * <p>ID 보조 기준이 없으면 같은 등급·점수끼리의 순서를 DB가 매번 다르게 정할 수 있고,
     * LIMIT/OFFSET 페이징에서 같은 시설이 두 페이지에 나오거나 아예 빠질 수 있다.
     */
    String ORDER_BY_GRADE =
            "order by facility.pawGradeLevel desc, facility.petScore desc, facility.facilityId";
  
    /**
     * "취향 비슷한 새곳"(similar) 카테고리 기반 후보 풀 — 좋아한 시설들의 카테고리와 겹치면서,
     * 아직 안 가봤고, 시설 단위로는 동반 자체가 막히지 않은 곳. 개별 반려동물 판별(DENIED 여부)은
     * 후보 수가 줄어든 뒤 서비스 레이어에서 {@code PetCheckJudgeService}로 한 번 더 거른다.
     */
    List<Facility> findAllByIsActiveTrueAndPetAllowedNotAndFacilityIdNotInAndCategoryIn(
            PetAllowed excludedPetAllowed,
            Collection<Long> excludedFacilityIds,
            Collection<FacilityCategory> categories
    );

    /**
     * {@code GET /api/v1/courses/regions}가 지역 선택 드롭다운을 채울 때 쓴다 — 동반 가능
     * 시설이 실제로 있는 (sido, sigungu) 조합만 내려준다. {@code sido}는 항상 값이 있고,
     * {@code sigungu}는 없을 수 있다(시/군/구 단위 데이터가 없는 시설).
     */
    @Query("""
            select distinct facility.sido as sido, facility.sigungu as sigungu from Facility facility
            where facility.isActive = true
            and facility.petAllowed <> com.freepets.domain.facility.entity.PetAllowed.DENIED
            and facility.sido is not null
            order by facility.sido, facility.sigungu
            """)
    List<SidoSigungu> findDistinctRegions();

    /**
     * {@code GET /api/v1/courses/preset} 배치가 지역×테마 후보를 뽑을 때 쓴다. {@code sigungu}가
     * 없으면 {@code sido} 전체를 대상으로 한다 — 파라미터가 null이면 그 조건은 무시된다.
     *
     * <p>preset뿐 아니라 liked/similar가 "식사 스톱"(카테고리 RESTAURANT) 후보를 지역 한정으로
     * 뽑을 때도 그대로 재사용한다 — 지역×카테고리로 후보를 좁히는 쿼리 모양이 완전히 같다.
     * {@code sido}가 없는(지역 필터를 안 받은) liked/similar 호출은 이 쿼리 대신 {@link
     * #findByCategoryWithinBoundingBox}로 좌표 기준으로 후보를 찾는다.
     */
    @Query("""
            select facility from Facility facility
            where facility.isActive = true
            and facility.petAllowed <> com.freepets.domain.facility.entity.PetAllowed.DENIED
            and facility.sido = :sido
            and (:sigungu is null or facility.sigungu = :sigungu)
            and facility.category in :categories
            """)
    List<Facility> findPresetCandidates(
            @Param("sido") String sido,
            @Param("sigungu") String sigungu,
            @Param("categories") Collection<FacilityCategory> categories
    );

    /**
     * {@code POST /api/v1/ai/course-check}가 DENIED 스톱의 대안을 찾을 때 쓴다. "같은 카테고리 ·
     * 이미 코스에 없음 · 좌표 있음 · 경계 사각형 안"까지만 DB에서 거르고, "그룹 전체가 갈 수
     * 있는지"(판별)와 "거리순 1곳"은 후보 수가 줄어든 뒤 서비스 레이어에서 처리한다 —
     * {@code findPresetCandidates}와 같은 이유.
     *
     * <p>지역 조건 없이 카테고리로만 걸렀던 이전 버전은 흔한 카테고리(예: RESTAURANT 전국
     * 1만여 곳)에서 전부 불러온 뒤에야 거리로 걸러 낭비가 컸다 — 경계 사각형은 {@code
     * com.freepets.domain.facility.service.BoundingBox}로 계산하고, {@code
     * idx_facilities_coordinate} 인덱스를 타는 {@code RADIUS_FILTER}와 같은 이유로 좌표 범위
     * 비교만 건다(정확한 반경 자르기는 사각형이 원의 외접이라 필요 없음 — 호출부가 이미 거리순
     * 정렬 후 상위 N개만 쓴다).
     */
    @Query("""
            select facility from Facility facility
            where facility.isActive = true
            and facility.petAllowed <> :excludedPetAllowed
            and facility.category = :category
            and facility.facilityId not in :excludedFacilityIds
            and facility.lat is not null
            and facility.lng is not null
            and facility.lat between :minimumLatitude and :maximumLatitude
            and facility.lng between :minimumLongitude and :maximumLongitude
            """)
    List<Facility> findAlternativeCandidates(
            @Param("category") FacilityCategory category,
            @Param("excludedPetAllowed") PetAllowed excludedPetAllowed,
            @Param("excludedFacilityIds") Collection<Long> excludedFacilityIds,
            @Param("minimumLatitude") BigDecimal minimumLatitude,
            @Param("maximumLatitude") BigDecimal maximumLatitude,
            @Param("minimumLongitude") BigDecimal minimumLongitude,
            @Param("maximumLongitude") BigDecimal maximumLongitude
    );

    /**
     * liked/similar가 지역 필터(sido) 없이 호출됐을 때 "식사 스톱"(카테고리 RESTAURANT) 후보를
     * {@code findPresetCandidates} 대신 이걸로 찾는다 — 지역명이 없어도 실제 좌표(예: 사용자가
     * 좋아한 시설 중 만족도가 가장 높은 곳)가 있으면 그 주변을 경계 사각형으로 좁힐 수 있다.
     * {@code findAlternativeCandidates}와 같은 이유로 정확한 반경 자르기는 필요 없다 — 호출부가
     * 이미 거리순 정렬 후 상위 N개만 쓴다.
     */
    @Query("""
            select facility from Facility facility
            where facility.isActive = true
            and facility.petAllowed <> com.freepets.domain.facility.entity.PetAllowed.DENIED
            and facility.category = :category
            and facility.lat is not null
            and facility.lng is not null
            and facility.lat between :minimumLatitude and :maximumLatitude
            and facility.lng between :minimumLongitude and :maximumLongitude
            """)
    List<Facility> findByCategoryWithinBoundingBox(
            @Param("category") FacilityCategory category,
            @Param("minimumLatitude") BigDecimal minimumLatitude,
            @Param("maximumLatitude") BigDecimal maximumLatitude,
            @Param("minimumLongitude") BigDecimal minimumLongitude,
            @Param("maximumLongitude") BigDecimal maximumLongitude
    );

    Optional<Facility> findByContentId(String contentId);

    List<Facility> findByContentIdIn(Collection<String> contentIds);

    /**
     * "취향 비슷한 새곳"(similar)에서 취향 프로필(만족도 기록)이 아예 없는 신규 유저용 대체
     * 후보 풀 — 좋아한 시설 기반 카테고리·태그 매치가 불가능하므로, 개인화 대신 리뷰 평점
     * 기준으로 대체 추천한다.
     */
    List<Facility> findAllByIsActiveTrueAndPetAllowedNot(PetAllowed excludedPetAllowed);

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

    /**
     * 백필이 전 시설을 페이지 단위로 훑는 데 쓴다.
     *
     * <p>{@code findAll(Pageable)}과 달리 전체 건수를 세지 않는다. 수만 행짜리 count를 페이지마다
     * 반복할 이유가 없다.
     */
    Slice<Facility> findAllBy(Pageable pageable);

    /**
     * 발자국 랭킹. 사용자 좌표가 없을 때 쓴다.
     *
     * <p>위치 권한을 거부해도 랭킹은 보여야 하므로 거리 없이도 조회할 수 있어야 한다.
     * 거리를 계산하지 않으니 좌표가 없는 시설도 그대로 포함된다.
     */
    @Query("select facility from Facility facility " + RANKING_FILTER + ORDER_BY_GRADE)
    List<Facility> searchRanking(
            @Param("category") FacilityCategory category,
            @Param("petAllowed") PetAllowed petAllowed,
            @Param("sidoCode") String sidoCode,
            @Param("sigunguCode") String sigunguCode,
            Pageable pageable
    );

    /** 발자국 랭킹 + 사용자 위치로부터의 거리. 반경 제한은 걸지 않는다. */
    @Query(SELECT_WITH_DISTANCE + RANKING_FILTER + COORDINATE_NOT_NULL_FILTER + ORDER_BY_GRADE)
    List<FacilityWithDistance> searchRankingWithDistance(
            @Param("userLatitudeRadian") double userLatitudeRadian,
            @Param("userLongitudeRadian") double userLongitudeRadian,
            @Param("category") FacilityCategory category,
            @Param("petAllowed") PetAllowed petAllowed,
            @Param("sidoCode") String sidoCode,
            @Param("sigunguCode") String sigunguCode,
            Pageable pageable
    );

    /** 발자국 랭킹 + 반경 제한. 경계 사각형이 인덱스를 타도록 {@link #RADIUS_FILTER}를 그대로 쓴다. */
    @Query(SELECT_WITH_DISTANCE + RANKING_FILTER + COORDINATE_NOT_NULL_FILTER + RADIUS_FILTER + ORDER_BY_GRADE)
    List<FacilityWithDistance> searchRankingWithinRadius(
            @Param("userLatitudeRadian") double userLatitudeRadian,
            @Param("userLongitudeRadian") double userLongitudeRadian,
            @Param("category") FacilityCategory category,
            @Param("petAllowed") PetAllowed petAllowed,
            @Param("sidoCode") String sidoCode,
            @Param("sigunguCode") String sigunguCode,
            @Param("minimumLatitude") BigDecimal minimumLatitude,
            @Param("maximumLatitude") BigDecimal maximumLatitude,
            @Param("minimumLongitude") BigDecimal minimumLongitude,
            @Param("maximumLongitude") BigDecimal maximumLongitude,
            @Param("radiusMeter") double radiusMeter,
            Pageable pageable
    );

    /**
     * 랭킹 전체 건수.
     *
     * @param isCoordinateGiven 좌표를 보낸 요청인지 여부. 좌표가 있으면 거리 계산 때문에 좌표 없는
     *                          시설이 목록에서 빠지므로, 건수도 같은 기준으로 세야 마지막 페이지가 맞는다
     */
    @Query(SELECT_COUNT + RANKING_FILTER
            + "and (:isCoordinateGiven = false or (facility.lat is not null and facility.lng is not null)) ")
    long countRanking(
            @Param("category") FacilityCategory category,
            @Param("petAllowed") PetAllowed petAllowed,
            @Param("sidoCode") String sidoCode,
            @Param("sigunguCode") String sigunguCode,
            @Param("isCoordinateGiven") boolean isCoordinateGiven
    );

    @Query(SELECT_COUNT + RANKING_FILTER + COORDINATE_NOT_NULL_FILTER + RADIUS_FILTER)
    long countRankingWithinRadius(
            @Param("userLatitudeRadian") double userLatitudeRadian,
            @Param("userLongitudeRadian") double userLongitudeRadian,
            @Param("category") FacilityCategory category,
            @Param("petAllowed") PetAllowed petAllowed,
            @Param("sidoCode") String sidoCode,
            @Param("sigunguCode") String sigunguCode,
            @Param("minimumLatitude") BigDecimal minimumLatitude,
            @Param("maximumLatitude") BigDecimal maximumLatitude,
            @Param("minimumLongitude") BigDecimal minimumLongitude,
            @Param("maximumLongitude") BigDecimal maximumLongitude,
            @Param("radiusMeter") double radiusMeter
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
