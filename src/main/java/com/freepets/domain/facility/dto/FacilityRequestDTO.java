package com.freepets.domain.facility.dto;

import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class FacilityRequestDTO {

    private FacilityRequestDTO() {}

    /**
     * 시설 목록 검색 조건.
     *
     * <p>위도·경도는 개인위치정보라 쿼리 스트링이 아닌 본문으로 받는다.
     * 쿼리 스트링은 웹 서버 액세스 로그와 APM 트레이스에 자동으로 남지만 본문은 남지 않는다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SearchRequest {

        // Swagger의 "Try it out" 예시 JSON을 그냥 눌러도 돌아가는 값으로 채워둔다(강릉 안목해변
        // 인근 좌표) — 본문(body) 안 enum 필드(category/petAllowed)는 쿼리 파라미터와 달리
        // Swagger UI가 개별 드롭다운으로 그려주지 못해서, 최소한 예시라도 바로 쓸 수 있게 한다.
        // 실제 서버 동작(위도·경도 필수 등)엔 영향 없다 — 순수 문서·예시용 애노테이션.
        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        @Schema(example = "37.7519")
        private Double latitude;

        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        @Schema(example = "128.8761")
        private Double longitude;

        @Size(max = 100, message = "검색어는 100자 이하로 입력해주세요.")
        @Schema(example = "카페")
        private String keyword;

        // Schema 탭에서 FacilityCategory 전체 값을 확인할 수 있다 — 여기 example은 그중 하나일 뿐.
        @Schema(example = "CAFE")
        private FacilityCategory category;

        @Schema(example = "ALLOWED")
        private PetAllowed petAllowed;

        /**
         * 검색 반경(m). 탐색 탭은 설정값(1000·3000·5000·10000) 중 하나를 보낸다.
         *
         * <p>비우면 반경 제한 없이 조회한다. 매장 찾기·스탬프 추가처럼 거리와 무관하게
         * 찾아야 하는 화면을 위해 남겨둔 값이며, 이때는 좌표 인덱스를 쓰지 못하므로
         * keyword나 category를 함께 보내는 편이 좋다.
         */
        @Min(value = 100, message = "검색 반경은 100m 이상이어야 합니다.")
        @Max(value = 100000, message = "검색 반경은 100000m 이하여야 합니다.")
        @Schema(example = "3000")
        private Integer radiusM;

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        @Schema(example = "0")
        private int page = 0;

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
        @Schema(example = "15")
        private int size = 15;
    }
}
