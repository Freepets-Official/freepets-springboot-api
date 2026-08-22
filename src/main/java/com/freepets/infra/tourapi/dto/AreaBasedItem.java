package com.freepets.infra.tourapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code areaBasedList2} 응답의 개별 항목. 전체 시설 집합(A)을 이룬다.
 *
 * <p>응답에 오지만 적재하지 않는 필드({@code zipcode} {@code createdtime} {@code mlevel} 등)는
 * 선언하지 않고 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AreaBasedItem(

        @JsonProperty("contentid") String contentId,
        @JsonProperty("contenttypeid") String contentTypeId,
        @JsonProperty("title") String title,
        @JsonProperty("addr1") String address,
        @JsonProperty("addr2") String addressDetail,

        /** GPS X좌표 = 경도 */
        @JsonProperty("mapx") String mapX,

        /** GPS Y좌표 = 위도 */
        @JsonProperty("mapy") String mapY,

        @JsonProperty("tel") String tel,
        @JsonProperty("firstimage") String imageUrl,
        @JsonProperty("firstimage2") String thumbnailUrl,
        @JsonProperty("cpyrhtDivCd") String copyrightType,
        @JsonProperty("modifiedtime") String modifiedTime,
        @JsonProperty("lDongRegnCd") String sidoCode,
        @JsonProperty("lDongSignguCd") String sigunguCode,
        @JsonProperty("lclsSystm1") String largeCategoryCode,
        @JsonProperty("lclsSystm2") String mediumCategoryCode,
        @JsonProperty("lclsSystm3") String smallCategoryCode
) {
}
