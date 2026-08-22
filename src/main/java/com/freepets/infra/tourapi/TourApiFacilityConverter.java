package com.freepets.infra.tourapi;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Component;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.service.PetConditionNormalizer;
import com.freepets.domain.facility.service.PetConditionParseResult;
import com.freepets.domain.facility.service.PetConditionParser;
import com.freepets.infra.tourapi.dto.AreaBasedItem;
import com.freepets.infra.tourapi.dto.PetTourItem;

import lombok.RequiredArgsConstructor;

/**
 * 관광공사 응답을 {@link Facility}로 옮긴다.
 *
 * <p>외부 필드가 바뀌어도 도메인이 흔들리지 않도록 변환을 infra 경계에 둔다.
 */
@Component
@RequiredArgsConstructor
public class TourApiFacilityConverter {

    private static final DateTimeFormatter MODIFIED_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 한반도 좌표 범위. 벗어나면 x/y가 뒤바뀐 것으로 보고 버린다. */
    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(33);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(39);
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(124);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(132);

    private final FacilityCategoryMapper facilityCategoryMapper;
    private final PetConditionParser petConditionParser;

    /**
     * 시설 한 건을 만든다.
     *
     * @param petTourItem     반려동물 동반 정보. 미등재 시설이면 {@code null}
     * @param regionNameTable 지역 코드를 이름으로 옮기는 표
     * @return 적재 대상이 아닌 분류(여행코스 등)면 {@code null}
     */
    public Facility convert(
            AreaBasedItem areaBasedItem,
            PetTourItem petTourItem,
            RegionNameTable regionNameTable
    ) {
        FacilityCategory category = facilityCategoryMapper.map(
                areaBasedItem.contentTypeId(),
                areaBasedItem.mediumCategoryCode()
        );
        if (category == null) {
            return null;
        }

        boolean petTourListed = petTourItem != null;
        PetConditionParseResult parsed = petTourListed
                ? petConditionParser.parse(
                        petTourItem.accompanyType(),
                        petTourItem.allowedAnimal(),
                        petTourItem.requiredMatter(),
                        petTourItem.etcAccompanyInfo(),
                        petTourItem.accidentRisk())
                : new PetConditionParseResult(PetAllowed.PENDING, null, List.of());

        Facility facility = Facility.builder()
                .contentId(areaBasedItem.contentId())
                .name(PetConditionNormalizer.normalize(areaBasedItem.title()))
                .category(category)
                .address(joinAddress(areaBasedItem))
                .lat(parseLatitude(areaBasedItem.mapY()))
                .lng(parseLongitude(areaBasedItem.mapX()))
                .phone(emptyToNull(PetConditionNormalizer.normalize(areaBasedItem.tel())))
                .largeCategoryCode(emptyToNull(areaBasedItem.largeCategoryCode()))
                .mediumCategoryCode(emptyToNull(areaBasedItem.mediumCategoryCode()))
                .smallCategoryCode(emptyToNull(areaBasedItem.smallCategoryCode()))
                .sidoCode(emptyToNull(areaBasedItem.sidoCode()))
                .sigunguCode(emptyToNull(areaBasedItem.sigunguCode()))
                .sido(regionNameTable.sidoNameOf(areaBasedItem.sidoCode()))
                .sigungu(regionNameTable.sigunguNameOf(
                        areaBasedItem.sidoCode(),
                        areaBasedItem.sigunguCode()))
                .imageUrl(toSecureUrl(areaBasedItem.imageUrl()))
                .thumbnailUrl(toSecureUrl(areaBasedItem.thumbnailUrl()))
                .copyrightType(emptyToNull(areaBasedItem.copyrightType()))
                .tourApiModifiedAt(parseModifiedTime(areaBasedItem.modifiedTime()))
                .accompanyType(petTourListed ? emptyToNull(petTourItem.accompanyType()) : null)
                .allowedAnimalText(petTourListed ? emptyToNull(petTourItem.allowedAnimal()) : null)
                .requiredMatterText(petTourListed ? emptyToNull(petTourItem.requiredMatter()) : null)
                .etcAccompanyText(petTourListed ? emptyToNull(petTourItem.etcAccompanyInfo()) : null)
                .accidentRiskText(petTourListed ? emptyToNull(petTourItem.accidentRisk()) : null)
                .petConditionHash(hashOf(petTourItem))
                .petAllowed(parsed.petAllowed())
                .maxWeight(parsed.maxWeight())
                .parserVersion(PetConditionParser.PARSER_VERSION)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(petTourListed)
                .build();

        facility.replaceRequirements(parsed.requirements());
        return facility;
    }

    private String joinAddress(AreaBasedItem item) {
        String address = PetConditionNormalizer.normalize(item.address());
        String detail = PetConditionNormalizer.normalize(item.addressDetail());

        if (address.isEmpty()) {
            return emptyToNull(detail);
        }
        return detail.isEmpty() ? address : address + " " + detail;
    }

    private BigDecimal parseLatitude(String rawValue) {
        return parseCoordinate(rawValue, MIN_LATITUDE, MAX_LATITUDE);
    }

    private BigDecimal parseLongitude(String rawValue) {
        return parseCoordinate(rawValue, MIN_LONGITUDE, MAX_LONGITUDE);
    }

    private BigDecimal parseCoordinate(
            String rawValue,
            BigDecimal minimum,
            BigDecimal maximum
    ) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            BigDecimal coordinate = new BigDecimal(rawValue.trim());
            boolean isInRange = coordinate.compareTo(minimum) >= 0
                    && coordinate.compareTo(maximum) <= 0;
            return isInRange ? coordinate : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDateTime parseModifiedTime(String rawValue) {
        if (rawValue == null || rawValue.length() != 14) {
            return null;
        }
        try {
            return LocalDateTime.parse(rawValue, MODIFIED_TIME_FORMAT);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 이미지 URL을 https로 맞춘다.
     *
     * <p>오퍼레이션마다 스킴이 섞여 오는데, http인 채로 두면 iOS ATS와 Android cleartext 정책에
     * 막혀 앱에서 표시되지 않는다.
     */
    private String toSecureUrl(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String url = rawValue.trim();
        return url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
    }

    /**
     * 조건 원문의 해시. 값이 그대로면 재파싱을 건너뛰는 데 쓴다.
     */
    private String hashOf(PetTourItem item) {
        if (item == null) {
            return null;
        }
        String joined = String.join("",
                PetConditionNormalizer.normalize(item.accompanyType()),
                PetConditionNormalizer.normalize(item.allowedAnimal()),
                PetConditionNormalizer.normalize(item.requiredMatter()),
                PetConditionNormalizer.normalize(item.etcAccompanyInfo()),
                PetConditionNormalizer.normalize(item.accidentRisk())
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
