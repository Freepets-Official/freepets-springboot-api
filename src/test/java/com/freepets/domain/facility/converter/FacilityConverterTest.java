package com.freepets.domain.facility.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.freepets.domain.facility.dto.FacilityResponseDTO;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityAmenity;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;

class FacilityConverterTest {

    private Facility createFacility() {
        return Facility.builder()
                .name("카페 파도살롱")
                .category(FacilityCategory.CAFE)
                .address("강원 강릉시 창해로 17")
                .lat(new BigDecimal("37.8000000"))
                .lng(new BigDecimal("128.9000000"))
                .petAllowed(PetAllowed.ALLOWED)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
    }

    @Test
    void toFacilityDetail_소개글도_태그도_없으면_ownerIntroduction이_null이다() {
        Facility facility = createFacility();

        FacilityResponseDTO.FacilityDetail detail =
                FacilityConverter.toFacilityDetail(facility, null, null, List.of(), 0L);

        assertThat(detail.ownerIntroduction()).isNull();
    }

    @Test
    void toFacilityDetail_소개글이나_태그가_있으면_ownerIntroduction에_담는다() {
        Facility facility = createFacility();
        facility.updateProfile(
                "대형견도 환영해요.",
                List.of(FacilityAmenity.WATER_BOWL, FacilityAmenity.OUTDOOR_TERRACE)
        );

        FacilityResponseDTO.FacilityDetail detail =
                FacilityConverter.toFacilityDetail(facility, null, null, List.of(), 0L);

        assertThat(detail.ownerIntroduction().introduction()).isEqualTo("대형견도 환영해요.");
        assertThat(detail.ownerIntroduction().amenityTags())
                .containsExactly(FacilityAmenity.WATER_BOWL, FacilityAmenity.OUTDOOR_TERRACE);
    }
}
