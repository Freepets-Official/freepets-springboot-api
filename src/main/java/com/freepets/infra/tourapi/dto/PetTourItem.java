package com.freepets.infra.tourapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code detailPetTour2} 응답의 개별 항목. 반려동물 동반 정보를 보유한 집합(B)을 이룬다.
 *
 * <p>{@code contentId}를 생략해 호출하면 전체 목록이 페이징으로 내려온다.
 * 응답에 {@code contenttypeid}는 포함되지 않는다.
 *
 * <p>구비 시설·비치·렌탈·구매 품목({@code rela*Prdlst}, {@code relaPosesFclty})은
 * 현재 응답 명세에 쓰이지 않아 적재하지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PetTourItem(

        @JsonProperty("contentid") String contentId,

        /** 동반 구분. {@code 전구역 동반가능} / {@code 일부구역 동반가능} */
        @JsonProperty("acmpyTypeCd") String accompanyType,

        /** 동반 가능 동물 */
        @JsonProperty("acmpyPsblCpam") String allowedAnimal,

        /** 동반 시 필요사항. 콤마로 구분된 코드성 값이다 */
        @JsonProperty("acmpyNeedMtr") String requiredMatter,

        /** 기타 동반 정보 */
        @JsonProperty("etcAcmpyInfo") String etcAccompanyInfo,

        /** 관련 사고 대비사항. 파싱 입력으로만 쓰고 화면에는 노출하지 않는다 */
        @JsonProperty("relaAcdntRiskMtr") String accidentRisk
) {
}
