package com.freepets.domain.facility.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class FacilityTest {

    private Facility createFacility() {
        return Facility.builder()
                .contentId("12345")
                .name("카페 파도살롱")
                .category(FacilityCategory.CAFE)
                .address("강원 강릉시 창해로 17")
                .lat(new BigDecimal("37.8000000"))
                .lng(new BigDecimal("128.9000000"))
                .petAllowed(PetAllowed.PENDING)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
    }

    /** 관광공사에서 다시 내려받은 값. 동반 조건이 우리가 확정한 것과 다르게 온 상황이다. */
    private Facility fetchedFacility() {
        return Facility.builder()
                .contentId("12345")
                .name("카페 파도살롱(관광공사 갱신 이름)")
                .category(FacilityCategory.CAFE)
                .address("강원 강릉시 창해로 17")
                .lat(new BigDecimal("37.8000000"))
                .lng(new BigDecimal("128.9000000"))
                .petAllowed(PetAllowed.DENIED)
                .maxWeight(new BigDecimal("5.00"))
                .maxWeightInclusive(false)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
    }

    private void confirm(Facility facility) {
        facility.confirmByOwner(
                PetAllowed.ALLOWED,
                new BigDecimal("10.00"),
                true,
                List.of(Requirement.LEASH),
                "리드줄 착용 시 실내 동반 가능"
        );
    }

    @Test
    void confirmByOwner_조건과_확정_시각을_반영한다() {
        Facility facility = createFacility();

        confirm(facility);

        assertThat(facility.getPetAllowed()).isEqualTo(PetAllowed.ALLOWED);
        assertThat(facility.getMaxWeight()).isEqualByComparingTo("10.00");
        assertThat(facility.getMaxWeightInclusive()).isTrue();
        assertThat(facility.getPetConditionRaw()).isEqualTo("리드줄 착용 시 실내 동반 가능");
        assertThat(facility.getConfirmedAt()).isNotNull();
        assertThat(facility.getCheckLists()).hasSize(1);
        assertThat(facility.getCheckLists().get(0).getType()).isEqualTo(Requirement.LEASH);
    }

    @Test
    void confirmByOwner_최대_체중이_없으면_경계_종류도_비운다() {
        Facility facility = createFacility();

        facility.confirmByOwner(PetAllowed.ALLOWED, null, true, List.of(), "동반 가능");

        assertThat(facility.getMaxWeight()).isNull();
        // 상한이 없는데 "이하"만 남으면 판별이 읽을 때 의미가 없다.
        assertThat(facility.getMaxWeightInclusive()).isNull();
    }

    @Test
    void updateFromTourApi_확정된_시설은_동반_조건을_덮어쓰지_않는다() {
        // 이 보호가 없으면 다음 동기화가 사장님이 직접 확정한 값을 지운다.
        Facility facility = createFacility();
        confirm(facility);

        facility.updateFromTourApi(fetchedFacility());

        assertThat(facility.getPetAllowed()).isEqualTo(PetAllowed.ALLOWED);
        assertThat(facility.getMaxWeight()).isEqualByComparingTo("10.00");
        assertThat(facility.getMaxWeightInclusive()).isTrue();
        assertThat(facility.getPetConditionRaw()).isEqualTo("리드줄 착용 시 실내 동반 가능");
        // 조건이 아닌 기본 정보는 그대로 갱신된다.
        assertThat(facility.getName()).isEqualTo("카페 파도살롱(관광공사 갱신 이름)");
    }

    @Test
    void updateFromTourApi_확정한_적이_없으면_동반_조건도_갱신한다() {
        Facility facility = createFacility();

        facility.updateFromTourApi(fetchedFacility());

        assertThat(facility.getPetAllowed()).isEqualTo(PetAllowed.DENIED);
        assertThat(facility.getMaxWeight()).isEqualByComparingTo("5.00");
        assertThat(facility.getMaxWeightInclusive()).isFalse();
    }
}
