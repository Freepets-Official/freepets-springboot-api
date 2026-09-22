package com.freepets.domain.facility.dto;

import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    /**
     * 발자국 랭킹 조회 조건. 모든 필드가 선택이며, 생략한 필터는 적용하지 않는다.
     *
     * <p>목록 검색과 달리 GET 쿼리 스트링으로 받는다. 랭킹은 필터 조합이 곧 링크로 공유될 수 있는
     * 화면이고, 좌표 없이도 조회되어야 하기 때문이다. 대신 좌표를 함께 보내면 개인위치정보가
     * 액세스 로그와 APM 트레이스에 남으므로 웹 서버 쪽 파라미터 마스킹이 필요하다.
     *
     * <p>필드 간 관계(위경도 짝, 반경의 좌표 의존, 시군구의 시도 의존)는 애너테이션으로 표현할 수
     * 없어 {@code FacilityQueryService}에서 검증한다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class RankingRequest {

        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        private Double latitude;

        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        private Double longitude;

        /** 시도 코드. 비우면 전국이다. */
        @Size(max = 10, message = "시도 코드는 10자 이하여야 합니다.")
        private String sidoCode;

        /** 시군구 코드. 시도 코드와 함께 보낼 때만 유효하다. */
        @Size(max = 10, message = "시군구 코드는 10자 이하여야 합니다.")
        private String sigunguCode;

        private FacilityCategory category;

        private PetAllowed petAllowed;

        /** 검색 반경(m). 화면의 1km·3km·10km 칩과 직접 입력을 함께 담는다. 좌표가 있어야 쓸 수 있다. */
        @Min(value = 100, message = "검색 반경은 100m 이상이어야 합니다.")
        @Max(value = 100000, message = "검색 반경은 100000m 이하여야 합니다.")
        private Integer radiusM;

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        private int page = 0;

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
        private int size = 20;

        public boolean isCoordinateGiven() {
            return latitude != null && longitude != null;
        }
    }

    /**
     * 전체 시설 목록 조회 조건.
     *
     * <p>랭킹 조건에서 좌표와 반경을 뺀 형태다. 거리순이 아니라 이름순으로 내려가므로 좌표가
     * 필요 없고, 좌표를 안 받으니 개인위치정보가 쿼리 스트링에 실릴 일도 없다.
     *
     * <p>지역을 얼마나 좁혔는지에 따라 어디서 답하는지가 갈린다. 관광공사는 조건에 맞는 전량을
     * 한 번에 받아오는 구조라 응답이 커지면 감당할 수 없어서다.
     *
     * <table>
     *   <tr><th>요청</th><th>응답 출처</th><th>규모</th></tr>
     *   <tr><td>시도 + 시군구</td><td>관광공사 실시간</td><td>최대 700여 건</td></tr>
     *   <tr><td>시도만</td><td>적재해둔 DB</td><td>9천 건 이상</td></tr>
     *   <tr><td>둘 다 없음(전국)</td><td>적재해둔 DB</td><td>5만 건에 가까움</td></tr>
     *   <tr><td>시군구만</td><td>400</td><td>시도 없이는 지역이 안 정해짐</td></tr>
     * </table>
     *
     * <p>세종특별자치시처럼 하위 시군구가 없는 시도는 시군구를 비워서 보내면 된다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class FacilityListRequest {

        /** 시도 코드. 비우면 전국이며, 이때는 관광공사가 아니라 적재해둔 DB에서 내려간다. */
        @Size(max = 10, message = "시도 코드는 10자 이하여야 합니다.")
        private String sidoCode;

        /** 시군구 코드. 비우면 시도 전체이며, 이때도 DB에서 내려간다. 시도 없이 단독으로는 못 쓴다. */
        @Size(max = 10, message = "시군구 코드는 10자 이하여야 합니다.")
        private String sigunguCode;

        private FacilityCategory category;

        private PetAllowed petAllowed;

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        private int page = 0;

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
        private int size = 15;

        /**
         * 빈 문자열로 온 시군구 코드를 "안 보냈다"와 같게 다룬다.
         *
         * <p>쿼리 스트링은 {@code ?sigunguCode=}처럼 값 없이 올 수 있는데, 그걸 그대로 조건에 실으면
         * 코드가 빈 문자열인 시설을 찾게 된다. 판단을 DTO가 들고 있어야 쓰는 쪽마다 어긋나지 않는다.
         */
        public String sigunguCodeOrNull() {
            return sigunguCode == null || sigunguCode.isBlank() ? null : sigunguCode;
        }

        /** 시도 코드도 같은 규칙으로 다룬다. {@link #sigunguCodeOrNull()} 참고. */
        public String sidoCodeOrNull() {
            return sidoCode == null || sidoCode.isBlank() ? null : sidoCode;
        }

        /** 지역을 지정하지 않은 전국 조회인지. 관광공사를 부를지 DB를 읽을지가 여기서 갈린다. */
        public boolean isNationwide() {
            return sidoCodeOrNull() == null;
        }
    }
}
