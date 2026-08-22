package com.freepets.infra.tourapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code ldongCode2} 응답 항목. {@code lDongListYn=Y}로 호출하면 시도-시군구가 함께 내려온다.
 *
 * <p>시설 응답에는 지역이 코드로만 오는데 발자국 랭킹의 지역 칩은 이름이 필요해서 매핑표를 만든다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LdongCodeItem(

        @JsonProperty("lDongRegnCd") String sidoCode,
        @JsonProperty("lDongRegnNm") String sidoName,
        @JsonProperty("lDongSignguCd") String sigunguCode,
        @JsonProperty("lDongSignguNm") String sigunguName
) {
}
