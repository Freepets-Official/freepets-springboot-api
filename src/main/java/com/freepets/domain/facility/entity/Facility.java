package com.freepets.domain.facility.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
                @Index(name = "idx_facilities_region", columnList = "sido_code, sigungu_code")
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

    @Column(name = "pet_score")
    private Integer petScore;

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

    @OneToMany(mappedBy = "facility", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlternativeFacility> alternativeFacilities = new ArrayList<>();

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
            boolean isDangerousBreedExcluded,
            List<String> requiredItems,
            String partialAreaNote,
            String unmappedConditionText
    ) {
        this.petConditionStatus = petConditionStatus;
        this.maxWeight = maxWeight;
        this.isDangerousBreedExcluded = isDangerousBreedExcluded;
        this.requiredItems = JsonListUtil.toJson(requiredItems);
        this.partialAreaNote = partialAreaNote;
        this.unmappedConditionText = unmappedConditionText;
    }

    public List<String> getRequiredItems() {
        return JsonListUtil.fromJson(requiredItems);
    }

    /**
     * 원문 근거 없이 저장된 maxWeight를 지운다 — {@code FacilityConditionLlmBatchService}의
     * 과거 데이터 청소 전용. 다른 파싱 결과(petConditionStatus 등)는 그대로 둔다.
     */
    public void clearMaxWeight() {
        this.maxWeight = null;
    }

}
