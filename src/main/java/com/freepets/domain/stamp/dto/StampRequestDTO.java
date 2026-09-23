package com.freepets.domain.stamp.dto;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class StampRequestDTO {

    private StampRequestDTO() {}

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {

        @NotNull(message = "시설 ID는 필수입니다.")
        private Long facilityId;

        // 본인 소유만 허용(StampCommandService가 검증). 비어 있어도 도장은 찍힌다.
        private List<Long> petIds;

        // 인증샷(선택). 없어도 도장은 찍힌다 — 기기 이전 마이그레이션은 로컬 URI라 못 올리는
        // 경우가 대부분이라 사진 없는 저장을 반드시 허용해야 한다.
        private MultipartFile photo;

        // 현재 위치(선택). facility 좌표와의 거리를 서버가 재서 isVerifiedOnSite를 판정하는 데만
        // 쓰고 저장하지 않는다 — 좌표를 저장하면 개인위치정보 수집이 되어 위치정보법 적용 범위가
        // 넓어진다(freepets-docs PR #48 권장사항). createdAt(마이그레이션)과 함께 오면 무시되고,
        // 대신 isVerifiedOnSite 값을 그대로 믿는다.
        private Double lat;
        private Double lng;

        // lat/lng가 없을 때(주로 마이그레이션)만 그대로 신뢰하는 값. 기본 false.
        private boolean isVerifiedOnSite;

        // 기기 이전 전용 — 로컬에 있던 도장을 올릴 때 원래 찍은 시각을 지정한다. 생략하면 지금
        // 시각으로 찍힌다. 미래 값은 이 검증(@PastOrPresent)이 COMMON400으로 거부한다.
        @PastOrPresent(message = "찍은 시각은 현재 이전이어야 합니다.")
        private LocalDateTime createdAt;

    }

}
