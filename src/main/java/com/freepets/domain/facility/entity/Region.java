package com.freepets.domain.facility.entity;

import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 법정동 기준 행정구역. 발자국 랭킹의 지역 칩이 쓴다.
 *
 * <p>관광공사 {@code ldongCode2}가 정본이다. 시설에서 지역을 역으로 모으지 않는 이유는, 지역명이
 * 시설 행마다 복사돼 있어 동기화가 중간에 끊기면 같은 코드에 옛 이름과 새 이름이 섞이기 때문이다
 * (강원도 / 강원특별자치도). 그러면 같은 지역의 칩이 두 개 만들어진다.
 *
 * <p>화면은 이름을 보여주고 클릭 시 코드를 보낸다. 이름으로 조회하지 않는 이유는 유일하지 않아서다
 * — {@code 중구}는 여섯 개 광역시에, {@code 고성군}은 두 개 도에 있다.
 */
@Getter
@Entity
@Table(
        name = "regions",
        indexes = @Index(name = "idx_regions_code", columnList = "sido_code, sigungu_code")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "sido_code", nullable = false, length = 10)
    private String sidoCode;

    @Column(nullable = false, length = 30)
    private String sido;

    /**
     * 시군구 코드. 세종특별자치시처럼 하위 시군구가 없는 시도가 있어 nullable이다.
     *
     * <p>유니크 제약을 걸지 않는다. Postgres는 유니크 인덱스에서 NULL을 서로 다른 값으로 보므로
     * {@code (36, NULL)}이 중복 삽입되는 것을 막지 못한다. 중복은 동기화 쪽에서 기존 행을 메모리로
     * 올려 비교하는 방식으로 막는다.
     */
    @Column(name = "sigungu_code", length = 10)
    private String sigunguCode;

    @Column(length = 30)
    private String sigungu;

    @Builder
    private Region(
            String sidoCode,
            String sido,
            String sigunguCode,
            String sigungu
    ) {
        this.sidoCode = sidoCode;
        this.sido = sido;
        this.sigunguCode = sigunguCode;
        this.sigungu = sigungu;
    }

    /** 지명 개편으로 이름만 바뀐 경우. 코드는 그대로이므로 행을 새로 만들지 않는다. */
    public void updateNames(
            String sido,
            String sigungu
    ) {
        this.sido = sido;
        this.sigungu = sigungu;
    }

    /** 시군구 칩을 만들 수 있는 행인지. 시도만 있는 행은 상위 칩으로만 쓰인다. */
    public boolean hasSigungu() {
        return sigunguCode != null && sigungu != null;
    }

}
