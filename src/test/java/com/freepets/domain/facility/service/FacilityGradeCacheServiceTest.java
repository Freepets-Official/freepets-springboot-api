package com.freepets.domain.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.repository.FacilityReviewAggregate;
import com.freepets.domain.review.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class FacilityGradeCacheServiceTest {

    private static final Long FACILITY_ID = 2L;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private FacilityGradeCacheService facilityGradeCacheService;

    private Facility createFacility() {
        Facility facility = Facility.builder()
                .name("카페 파도살롱")
                .category(FacilityCategory.CAFE)
                .lat(new BigDecimal("37.8000000"))
                .lng(new BigDecimal("128.9000000"))
                .petAllowed(PetAllowed.ALLOWED)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();

        ReflectionTestUtils.setField(facility, "facilityId", FACILITY_ID);

        return facility;
    }

    @Test
    @DisplayName("리뷰 집계를 점수·리뷰 수·등급으로 저장한다")
    void 리뷰_집계를_점수_리뷰_수_등급으로_저장한다() {
        Facility facility = createFacility();
        when(facilityRepository.findById(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(reviewRepository.aggregateByFacilityId(eq(FACILITY_ID), any(ReviewReportStatus.class)))
                .thenReturn(Optional.of(new FacilityReviewAggregate(
                        FACILITY_ID, 96L, 88.4, 4.5, 4.8, 4.2
                )));

        facilityGradeCacheService.refresh(FACILITY_ID);

        assertThat(facility.getPetScore()).isEqualTo(88.4);
        assertThat(facility.getReviewCount()).isEqualTo(96L);
        assertThat(facility.getPawGradeLevel()).isEqualTo(4);
    }

    @Test
    @DisplayName("등급 판정은 반올림 전 원점수로 한다")
    void 등급_판정은_반올림_전_원점수로_한다() {
        Facility facility = createFacility();
        when(facilityRepository.findById(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(reviewRepository.aggregateByFacilityId(eq(FACILITY_ID), any(ReviewReportStatus.class)))
                .thenReturn(Optional.of(new FacilityReviewAggregate(
                        FACILITY_ID, 90L, 87.96, 4.4, 4.4, 4.4
                )));

        facilityGradeCacheService.refresh(FACILITY_ID);

        // 87.96은 표시할 때 88.0이 되지만, 88점이 기준인 4등급에는 못 미친다.
        assertThat(facility.getPawGradeLevel()).isEqualTo(3);
    }

    @Test
    @DisplayName("적격 리뷰가 없으면 이전 점수를 지운다")
    void 적격_리뷰가_없으면_이전_점수를_지운다() {
        Facility facility = createFacility();
        facility.applyReviewAggregate(new FacilityReviewAggregate(
                FACILITY_ID, 160L, 96.2, 4.9, 4.9, 4.9
        ));

        when(facilityRepository.findById(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(reviewRepository.aggregateByFacilityId(eq(FACILITY_ID), any(ReviewReportStatus.class)))
                .thenReturn(Optional.empty());

        facilityGradeCacheService.refresh(FACILITY_ID);

        // 0점으로 두면 "최악의 시설"로 정렬되고, 이전 점수를 남기면 랭킹에 계속 뜬다.
        assertThat(facility.getPetScore()).isNull();
        assertThat(facility.getReviewCount()).isZero();
        assertThat(facility.getPawGradeLevel()).isZero();
    }

    @Test
    @DisplayName("없는 시설이면 조용히 넘어간다")
    void 없는_시설이면_조용히_넘어간다() {
        when(facilityRepository.findById(FACILITY_ID)).thenReturn(Optional.empty());

        facilityGradeCacheService.refresh(FACILITY_ID);
    }

}
