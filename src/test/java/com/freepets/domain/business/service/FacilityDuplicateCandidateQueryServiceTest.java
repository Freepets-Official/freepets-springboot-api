package com.freepets.domain.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.repository.FacilityWithDistance;

@ExtendWith(MockitoExtension.class)
class FacilityDuplicateCandidateQueryServiceTest {

    private static final double LAT = 37.8;
    private static final double LNG = 128.9;

    @Mock
    private FacilityRepository facilityRepository;

    private FacilityDuplicateCandidateQueryService facilityDuplicateCandidateQueryService;

    private Facility createFacility(
            String name,
            FacilitySource source
    ) {
        return Facility.builder()
                .name(name)
                .category(FacilityCategory.CAFE)
                .address("강원 강릉시 창해로 17")
                .lat(new BigDecimal("37.8000000"))
                .lng(new BigDecimal("128.9000000"))
                .petAllowed(PetAllowed.ALLOWED)
                .source(source)
                .isActive(true)
                .petTourListed(source == FacilitySource.TOUR_API)
                .build();
    }

    private void givenNearby(FacilityWithDistance... nearby) {
        when(facilityRepository.searchWithinRadius(
                anyDouble(), anyDouble(), any(), any(), any(),
                any(), any(), any(), any(), anyDouble(), any()
        )).thenReturn(List.of(nearby));
    }

    @Test
    void 이름이_비슷한_후보만_반환한다() {
        facilityDuplicateCandidateQueryService = new FacilityDuplicateCandidateQueryService(facilityRepository);
        givenNearby(
                new FacilityWithDistance(createFacility("카페 파도살롱", FacilitySource.TOUR_API), 30.0),
                new FacilityWithDistance(createFacility("김밥천국", FacilitySource.BUSINESS_SELF), 50.0)
        );

        List<BusinessResponseDTO.FacilityDuplicateCandidate> candidates =
                facilityDuplicateCandidateQueryService.findCandidates("카페 파도살롱", LAT, LNG);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).name()).isEqualTo("카페 파도살롱");
    }

    @Test
    void 출처와_무관하게_후보에_포함한다() {
        facilityDuplicateCandidateQueryService = new FacilityDuplicateCandidateQueryService(facilityRepository);
        givenNearby(
                new FacilityWithDistance(createFacility("카페 파도살롱", FacilitySource.TOUR_API), 10.0),
                new FacilityWithDistance(createFacility("카페 파도살롱 본점", FacilitySource.BUSINESS_SELF), 40.0)
        );

        List<BusinessResponseDTO.FacilityDuplicateCandidate> candidates =
                facilityDuplicateCandidateQueryService.findCandidates("카페 파도살롱", LAT, LNG);

        assertThat(candidates).extracting(BusinessResponseDTO.FacilityDuplicateCandidate::source)
                .containsExactlyInAnyOrder(FacilitySource.TOUR_API, FacilitySource.BUSINESS_SELF);
    }

    @Test
    void 거리순_정렬을_그대로_유지한다() {
        facilityDuplicateCandidateQueryService = new FacilityDuplicateCandidateQueryService(facilityRepository);
        givenNearby(
                new FacilityWithDistance(createFacility("카페 파도살롱 1", FacilitySource.TOUR_API), 10.0),
                new FacilityWithDistance(createFacility("카페 파도살롱 2", FacilitySource.TOUR_API), 60.0)
        );

        List<BusinessResponseDTO.FacilityDuplicateCandidate> candidates =
                facilityDuplicateCandidateQueryService.findCandidates("카페 파도살롱", LAT, LNG);

        assertThat(candidates).extracting(BusinessResponseDTO.FacilityDuplicateCandidate::distanceMeters)
                .containsExactly(10.0, 60.0);
    }

    @Test
    void 최대_5건까지만_반환한다() {
        facilityDuplicateCandidateQueryService = new FacilityDuplicateCandidateQueryService(facilityRepository);
        FacilityWithDistance[] nearby = new FacilityWithDistance[7];
        for (int i = 0; i < nearby.length; i++) {
            nearby[i] = new FacilityWithDistance(createFacility("카페 파도살롱 " + i, FacilitySource.TOUR_API), i * 10.0);
        }
        givenNearby(nearby);

        List<BusinessResponseDTO.FacilityDuplicateCandidate> candidates =
                facilityDuplicateCandidateQueryService.findCandidates("카페 파도살롱", LAT, LNG);

        assertThat(candidates).hasSize(5);
    }
}
