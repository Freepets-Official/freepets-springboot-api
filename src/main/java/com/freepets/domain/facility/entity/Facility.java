package com.freepets.domain.facility.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.freepets.domain.review.repository.FacilityReviewAggregate;
import com.freepets.global.entity.BaseEntity;
import com.freepets.global.util.JsonListUtil;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "facilities",
        indexes = {
                @Index(name = "idx_facilities_content_id", columnList = "content_id", unique = true),
                @Index(name = "idx_facilities_coordinate", columnList = "lat, lng"),
                @Index(name = "idx_facilities_category", columnList = "category"),
                @Index(name = "idx_facilities_region", columnList = "sido_code, sigungu_code"),

                // 발자국 랭킹의 정렬 순서(등급 → 점수 → ID)를 그대로 담는다. 랭킹 조회는 등급을
                // 받은 시설만 보므로 앞쪽 일부만 읽고 끝난다.
                @Index(
                        name = "idx_facilities_paw_grade_ranking",
                        columnList = "paw_grade_level desc, pet_score desc, facility_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Facility extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "facility_id")
    private Long facilityId;

    // ------------------------------------------------------------------
    // 관광공사 기본 정보 (areaBasedList2)
    // ------------------------------------------------------------------

    /** 관광공사 콘텐츠 ID. 사업자가 직접 등록한 시설에는 없으므로 nullable이다. */
    @Column(name = "content_id", length = 20)
    private String contentId;

    @Column(length = 200, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FacilityCategory category;

    @Column(length = 300)
    private String address;

    /** 위도. 관광공사 응답의 {@code mapy}에 해당한다. */
    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    /** 경도. 관광공사 응답의 {@code mapx}에 해당한다. */
    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    /**
     * 연락처. 관광공사 {@code tel}은 번호만 오는 게 아니라
     * {@code "관리사무소 041-930-6883, 안내센터 041-930-6885"}처럼 안내문이 담겨 온다.
     * 길이를 제한하지 않고 원문 그대로 보관한다.
     */
    @Column(columnDefinition = "TEXT")
    private String phone;

    @Column(name = "large_category_code", length = 10)
    private String largeCategoryCode;

    @Column(name = "medium_category_code", length = 10)
    private String mediumCategoryCode;

    @Column(name = "small_category_code", length = 10)
    private String smallCategoryCode;

    @Column(name = "sido_code", length = 10)
    private String sidoCode;

    @Column(name = "sigungu_code", length = 10)
    private String sigunguCode;

    @Column(length = 30)
    private String sido;

    @Column(length = 30)
    private String sigungu;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /** 공공누리 저작권 유형. Type3는 변경금지이므로 이미지 가공 전에 확인해야 한다. */
    @Column(name = "copyright_type", length = 10)
    private String copyrightType;

    /** 관광공사가 콘텐츠를 갱신한 시점. 출처 표기와 변경 감지에 쓴다. */
    @Column(name = "tour_api_modified_at")
    private LocalDateTime tourApiModifiedAt;

    // ------------------------------------------------------------------
    // 반려동물 동반 조건 원문 (detailPetTour2)
    // ------------------------------------------------------------------

    /** 동반 구분. {@code 전구역 동반가능} / {@code 일부구역 동반가능} 두 값만 관측된다. */
    @Column(name = "accompany_type", length = 200)
    private String accompanyType;

    /** 동반 가능 동물 원문. 체중 상한·소형견 한정을 여기서 추출한다. */
    @Column(name = "allowed_animal_text", columnDefinition = "TEXT")
    private String allowedAnimalText;

    /** 동반 시 필요사항 원문. 콤마로 구분된 코드성 값이라 사전 매핑으로 처리한다. */
    @Column(name = "required_matter_text", columnDefinition = "TEXT")
    private String requiredMatterText;

    /** 기타 동반 정보 원문. */
    @Column(name = "etc_accompany_text", columnDefinition = "TEXT")
    private String etcAccompanyText;

    /** 사고 대비사항 원문. 파싱 입력으로만 쓰고 화면에는 노출하지 않는다. */
    @Column(name = "accident_risk_text", columnDefinition = "TEXT")
    private String accidentRiskText;

    /** 위 원문들의 해시. 값이 그대로면 재파싱을 건너뛴다. */
    @Column(name = "pet_condition_hash", length = 64)
    private String petConditionHash;

    // ------------------------------------------------------------------
    // 파싱 결과
    // ------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "pet_allowed", nullable = false, length = 20)
    private PetAllowed petAllowed;

    /** 동반 가능한 최대 체중(kg). 원문에 상한이 명시된 경우에만 채워진다. */
    @Column(name = "max_weight", precision = 5, scale = 2)
    private BigDecimal maxWeight;

    /**
     * maxWeight 경계 포함 여부. {@code TRUE}="이하"(그 체중까지 포함), {@code FALSE}="미만"(그
     * 체중은 제외), {@code null}=maxWeight가 없거나 원문에서 경계 종류를 알 수 없음. maxWeight가
     * {@code null}이면 이 값도 항상 {@code null}이어야 한다.
     */
    @Column(name = "max_weight_inclusive")
    private Boolean maxWeightInclusive;

    /** 파싱 규칙 버전. 규칙을 고치면 올려서 전량 재파싱 대상으로 만든다. */
    @Column(name = "parser_version", nullable = false)
    private int parserVersion;

    // ------------------------------------------------------------------
    // LLM 조건 파싱 결과 (FacilityConditionLlmParser) — pet_allowed/maxWeight/checkLists와는
    // 별개 축. "조건 원문을 얼마나 구조화했는지"를 나타내며, 판별 엔진이 직접 읽는 값은 아니다.
    // ------------------------------------------------------------------

    /** 적재 직후 기본값은 NOT_PROCESSED — 라이브 DB엔 이미 시설이 있어 컬럼 추가 시 기본값이 필요하다. */
    @ColumnDefault("'NOT_PROCESSED'")
    @Enumerated(EnumType.STRING)
    @Column(name = "pet_condition_status", nullable = false, length = 20)
    private PetConditionStatus petConditionStatus;

    @ColumnDefault("false")
    @Column(name = "is_dangerous_breed_excluded", nullable = false)
    private boolean isDangerousBreedExcluded;

    /**
     * ["목줄 착용", ...] — 화면 표시용 문구. 판별 엔진이 쓰는 requirements(Requirement 목록)와는 별개.
     * 저장은 JSON 문자열, 읽기는 {@link #getRequiredItems()}로 List&lt;String&gt; 반환 — Lombok
     * 기본 getter는 끄고 아래 커스텀 getter만 노출한다.
     */
    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_items", columnDefinition = "json")
    private String requiredItems;

    /**
     * ["입마개 착용", ...] — 맹견(위험 품종)일 때만 추가로 지켜야 하는 조건. 원문에 "맹견의 경우
     * 입마개 착용 필수"처럼 특정 품종에만 걸리는 조건이 있어도 {@link #requiredItems}(전체
     * 방문객 대상)엔 안 담기고, {@code isDangerousBreedExcluded}도 "배제"가 아니라서 false로
     * 남아 이 정보가 어디에도 안 남던 문제를 위해 추가했다. 판별 엔진(PetCheckJudgeService)이
     * 맹견 배제 시설이 아니면서 맹견을 데려온 경우에만 조건 안내에 포함해 읽는다.
     */
    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dangerous_breed_required_items", columnDefinition = "json")
    private String dangerousBreedRequiredItems;

    @Column(name = "partial_area_note", columnDefinition = "TEXT")
    private String partialAreaNote;

    /** 컬럼으로 못 담은 잔여 원문. 비어있지 않으면 petConditionStatus가 AMBIGUOUS다. */
    @Column(name = "unmapped_condition_text", columnDefinition = "TEXT")
    private String unmappedConditionText;

    // ------------------------------------------------------------------
    // 적재 메타
    // ------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FacilitySource source;

    /** 관광공사가 비표출로 내린 콘텐츠는 삭제하지 않고 이 값을 내린다. */
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    /**
     * 관광공사 반려동물 동반 목록에 등재된 시설인지 여부.
     *
     * <p>{@code PENDING}이 "조건이 비어 있음"에서 온 것인지 "정보 자체가 없음"에서 온 것인지
     * 구분해두면, 신뢰도 차등 부여와 향후 정책 변경을 재적재 없이 처리할 수 있다.
     */
    @Column(name = "pet_tour_listed", nullable = false)
    private boolean petTourListed;

    // ------------------------------------------------------------------
    // 우리 데이터 — 적재 배치가 건드리지 않는다
    // ------------------------------------------------------------------

    /**
     * 화면에 그대로 보여줄 동반 조건 안내문.
     *
     * <p>관광공사 원문({@code allowedAnimalText} 등)을 이어 붙여 만들지 않는다. 원문은 표현이
     * 제각각이라 그대로 노출하면 잘못 읽히기 쉬워, 사람이 확인해 정리한 문장을 여기에 넣는다.
     *
     * <p>적재 배치가 건드리지 않는 우리 데이터다. {@code updateFromTourApi}에서 제외되어 있으므로
     * 동기화가 다시 돌아도 지워지지 않는다.
     */
    @Column(name = "pet_condition_raw", columnDefinition = "TEXT")
    private String petConditionRaw;

    /**
     * 리뷰에서 계산한 친화도 점수(0~100). 리뷰가 바뀔 때 {@code FacilityGradeCacheService}가 갱신한다.
     *
     * <p>정수가 아니라 실수다. 등급 판정은 반올림 <b>전</b> 원점수로 해야 하기 때문이다.
     * 87.96을 88로 저장해두면 88점이 기준인 4등급으로 잘못 올라간다.
     *
     * <p>적격 리뷰가 한 건도 없으면 {@code null}이다. 0.0은 "최악의 시설"이라는 뜻이 되어버린다.
     */
    @Column(name = "pet_score")
    private Double petScore;

    /**
     * 등급 산정에 쓰인 리뷰 수. 승인된 신고가 달린 리뷰는 빠진 값이다.
     *
     * <p>점수만으로는 등급이 정해지지 않아({@link PetFriendlyGrade#ofScore}) 함께 저장한다.
     * 이 값이 있어야 랭킹 조회가 리뷰 테이블을 보지 않고 시설만으로 끝난다.
     */
    @ColumnDefault("0")
    @Column(name = "review_count", nullable = false)
    private long reviewCount;

    /**
     * 발자국 등급 레벨(1~5). 등급을 못 받았으면 {@link PetFriendlyGrade#NO_GRADE_LEVEL}이다.
     *
     * <p>점수와 리뷰 수에서 유도되는 값이지만 따로 저장한다. 등급 순 정렬은 점수 순 정렬과 다르기
     * 때문이다 — 95점·리뷰 20건은 1등급, 85점·리뷰 100건은 3등급이라 뒤쪽이 위에 와야 한다.
     * 정렬을 SQL에서 하려면 이 값이 컬럼으로 있어야 하고, {@code case when}으로 계산하면
     * 등급 임계값이 {@link PetFriendlyGrade}와 SQL 양쪽에 적히게 된다.
     */
    @ColumnDefault("0")
    @Column(name = "paw_grade_level", nullable = false)
    private int pawGradeLevel;

    @Column(name = "space_rating")
    private Float spaceRating;

    @Column(name = "customer_service")
    private Float customerService;

    private Float amenities;

    /** 사업자·사용자 확인으로 조건이 확정된 시점. */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /**
     * 목록 조회는 시설마다 요구조건을 함께 내려준다. 지연 로딩만 두면 시설 수만큼 쿼리가 나가므로
     * 묶어서 가져온다. 페이징 쿼리에 join fetch를 걸면 Hibernate가 전체를 메모리로 올리기 때문에
     * 이 경우에는 batch fetch가 맞는 해법이다.
     */
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "facility", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CheckList> checkLists = new ArrayList<>();

    @Builder
    private Facility(
            String contentId,
            String name,
            FacilityCategory category,
            String address,
            BigDecimal lat,
            BigDecimal lng,
            String phone,
            String largeCategoryCode,
            String mediumCategoryCode,
            String smallCategoryCode,
            String sidoCode,
            String sigunguCode,
            String sido,
            String sigungu,
            String imageUrl,
            String thumbnailUrl,
            String copyrightType,
            LocalDateTime tourApiModifiedAt,
            String accompanyType,
            String allowedAnimalText,
            String requiredMatterText,
            String etcAccompanyText,
            String accidentRiskText,
            String petConditionHash,
            PetAllowed petAllowed,
            BigDecimal maxWeight,
            Boolean maxWeightInclusive,
            int parserVersion,
            PetConditionStatus petConditionStatus,
            FacilitySource source,
            boolean isActive,
            boolean petTourListed
    ) {
        this.contentId = contentId;
        this.name = name;
        this.category = category;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.phone = phone;
        this.largeCategoryCode = largeCategoryCode;
        this.mediumCategoryCode = mediumCategoryCode;
        this.smallCategoryCode = smallCategoryCode;
        this.sidoCode = sidoCode;
        this.sigunguCode = sigunguCode;
        this.sido = sido;
        this.sigungu = sigungu;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.copyrightType = copyrightType;
        this.tourApiModifiedAt = tourApiModifiedAt;
        this.accompanyType = accompanyType;
        this.allowedAnimalText = allowedAnimalText;
        this.requiredMatterText = requiredMatterText;
        this.etcAccompanyText = etcAccompanyText;
        this.accidentRiskText = accidentRiskText;
        this.petConditionHash = petConditionHash;
        this.petAllowed = petAllowed;
        this.maxWeight = maxWeight;
        this.maxWeightInclusive = maxWeightInclusive;
        this.parserVersion = parserVersion;
        this.petConditionStatus = petConditionStatus != null ? petConditionStatus : PetConditionStatus.NOT_PROCESSED;
        this.source = source;
        this.isActive = isActive;
        this.petTourListed = petTourListed;
    }

    /**
     * 관광공사에서 다시 내려받은 값으로 갱신한다.
     *
     * <p>리뷰 집계·신뢰도 같은 우리 데이터는 건드리지 않는다.
     * 전체 필드를 덮어쓰면 동기화 배치가 돌 때마다 그것들이 초기화된다.
     */
    public void updateFromTourApi(Facility fetched) {
        // petConditionHash가 실제로 바뀌었다면 조건 원문이 바뀐 것 — LLM 조건 파싱이 새 원문으로
        // 다시 돌게 NOT_PROCESSED로 되돌린다. 최초 적재(petConditionHash가 아직 없음)는 제외한다.
        boolean conditionTextChanged = this.petConditionHash != null
                && !this.petConditionHash.equals(fetched.petConditionHash);

        this.name = fetched.name;
        this.category = fetched.category;
        this.address = fetched.address;
        this.lat = fetched.lat;
        this.lng = fetched.lng;
        this.phone = fetched.phone;
        this.largeCategoryCode = fetched.largeCategoryCode;
        this.mediumCategoryCode = fetched.mediumCategoryCode;
        this.smallCategoryCode = fetched.smallCategoryCode;
        this.sidoCode = fetched.sidoCode;
        this.sigunguCode = fetched.sigunguCode;
        this.sido = fetched.sido;
        this.sigungu = fetched.sigungu;
        this.imageUrl = fetched.imageUrl;
        this.thumbnailUrl = fetched.thumbnailUrl;
        this.copyrightType = fetched.copyrightType;
        this.tourApiModifiedAt = fetched.tourApiModifiedAt;
        this.accompanyType = fetched.accompanyType;
        this.allowedAnimalText = fetched.allowedAnimalText;
        this.requiredMatterText = fetched.requiredMatterText;
        this.etcAccompanyText = fetched.etcAccompanyText;
        this.accidentRiskText = fetched.accidentRiskText;
        this.petConditionHash = fetched.petConditionHash;
        this.petAllowed = fetched.petAllowed;
        this.maxWeight = fetched.maxWeight;
        this.maxWeightInclusive = fetched.maxWeightInclusive;
        this.parserVersion = fetched.parserVersion;
        this.petTourListed = fetched.petTourListed;
        this.isActive = fetched.isActive;

        if (conditionTextChanged) {
            this.petConditionStatus = PetConditionStatus.NOT_PROCESSED;
        }
    }

    public void deactivate() {
        this.isActive = false;
    }

    /**
     * 코스 추천(preset/liked/similar)이 후보로 삼을 수 있는 상태인지 — 비활성화됐거나 동반이
     * 아예 불가면 더는 추천 대상이 아니다. {@code updateFromTourApi}로 이 값이 바뀌는 시점을
     * 서비스 레이어(FacilityUpsertService)가 감지해 캐시 무효화 이벤트를 발행하는 데 쓴다.
     */
    public boolean isEligibleForRecommendation() {
        return isActive && petAllowed != PetAllowed.DENIED;
    }

    public void replaceRequirements(List<Requirement> requirements) {
        this.checkLists.clear();
        requirements.forEach(requirement -> this.checkLists.add(
                CheckList.builder()
                        .facility(this)
                        .type(requirement)
                        .isChecked(false)
                        .build()
        ));
    }

    /**
     * {@code FacilityConditionLlmParser}(LLM 조건 파싱)의 결과를 반영한다. 언제 호출할지(신규 시설
     * lazy-sync 등)는 이 엔티티 범위 밖 — 서비스 레이어에서 결정한다.
     */
    public void applyParsedCondition(
            PetConditionStatus petConditionStatus,
            BigDecimal maxWeight,
            Boolean maxWeightInclusive,
            boolean isDangerousBreedExcluded,
            List<String> requiredItems,
            List<String> dangerousBreedRequiredItems,
            String partialAreaNote,
            String unmappedConditionText
    ) {
        this.petConditionStatus = petConditionStatus;
        this.maxWeight = maxWeight;
        this.maxWeightInclusive = maxWeightInclusive;
        this.isDangerousBreedExcluded = isDangerousBreedExcluded;
        this.requiredItems = JsonListUtil.toJson(requiredItems);
        this.dangerousBreedRequiredItems = JsonListUtil.toJson(dangerousBreedRequiredItems);
        this.partialAreaNote = partialAreaNote;
        this.unmappedConditionText = unmappedConditionText;
    }

    public List<String> getRequiredItems() {
        return JsonListUtil.fromJson(requiredItems);
    }

    public List<String> getDangerousBreedRequiredItems() {
        return JsonListUtil.fromJson(dangerousBreedRequiredItems);
    }

    /**
     * 리뷰 집계 결과를 시설에 반영한다. 랭킹 조회가 읽는 값이다.
     *
     * <p>{@code aggregate}가 {@code null}이면 적격 리뷰가 한 건도 없다는 뜻이다. 이때 점수를 0으로
     * 두면 "최악의 시설"로 정렬되므로 {@code null}로 되돌리고, 등급도 없음으로 내린다. 리뷰가
     * 전부 삭제되거나 신고로 빠졌을 때 예전 점수가 남지 않게 하려면 이 되돌림이 필요하다.
     *
     * <p>등급 레벨은 표시용으로 반올림하기 전 원점수로 판정한다.
     */
    public void applyReviewAggregate(FacilityReviewAggregate aggregate) {
        if (aggregate == null) {
            this.petScore = null;
            this.reviewCount = 0;
            this.pawGradeLevel = PetFriendlyGrade.NO_GRADE_LEVEL;
            this.spaceRating = null;
            this.customerService = null;
            this.amenities = null;
            return;
        }

        this.petScore = aggregate.averageScore();
        this.reviewCount = aggregate.reviewCount();
        this.pawGradeLevel = PetFriendlyGrade.levelOf(
                PetFriendlyGrade.ofScore(aggregate.averageScore(), aggregate.reviewCount())
        );
        this.spaceRating = (float) aggregate.averageSpace();
        this.customerService = (float) aggregate.averageStaff();
        this.amenities = (float) aggregate.averageAmenity();
    }

    /**
     * 원문 근거 없이 저장된 maxWeight를 지운다 — {@code FacilityConditionLlmBatchService}의
     * 과거 데이터 청소 전용. 다른 파싱 결과(petConditionStatus 등)는 그대로 둔다.
     */
    public void clearMaxWeight() {
        this.maxWeight = null;
        this.maxWeightInclusive = null;
    }

}
