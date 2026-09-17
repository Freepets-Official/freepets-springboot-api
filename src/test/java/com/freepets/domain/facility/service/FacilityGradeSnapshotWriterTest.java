package com.freepets.domain.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilityGradeSnapshot;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityGradeSnapshotRepository;

@ExtendWith(MockitoExtension.class)
class FacilityGradeSnapshotWriterTest {

    private static final Long FACILITY_ID = 6L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 17);

    @Mock
    private FacilityGradeSnapshotRepository facilityGradeSnapshotRepository;

    @InjectMocks
    private FacilityGradeSnapshotWriter facilityGradeSnapshotWriter;

    @Test
    void snapshotPage_오늘_스냅샷이_없으면_적재한다() {
        Facility facility = facility();
        when(facilityGradeSnapshotRepository.existsByFacility_FacilityIdAndSnapshotDate(FACILITY_ID, TODAY))
                .thenReturn(false);

        long snapshotted = facilityGradeSnapshotWriter.snapshotPage(List.of(facility), TODAY);

        assertThat(snapshotted).isEqualTo(1);
        ArgumentCaptor<FacilityGradeSnapshot> captor = ArgumentCaptor.forClass(FacilityGradeSnapshot.class);
        verify(facilityGradeSnapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getFacility()).isEqualTo(facility);
        assertThat(captor.getValue().getSnapshotDate()).isEqualTo(TODAY);
        assertThat(captor.getValue().getPawGradeLevel()).isEqualTo(4);
        assertThat(captor.getValue().getPetScore()).isEqualTo(88.4);
        assertThat(captor.getValue().getReviewCount()).isEqualTo(96L);
    }

    /** 스케줄러가 같은 날 다시 돌아도(재시작 등) 하루 1건만 남아야 한다. */
    @Test
    void snapshotPage_오늘_이미_스냅샷이_있으면_건너뛴다() {
        when(facilityGradeSnapshotRepository.existsByFacility_FacilityIdAndSnapshotDate(FACILITY_ID, TODAY))
                .thenReturn(true);

        long snapshotted = facilityGradeSnapshotWriter.snapshotPage(List.of(facility()), TODAY);

        assertThat(snapshotted).isZero();
        verify(facilityGradeSnapshotRepository, never()).save(any());
    }

    @Test
    void deleteBefore_보관_기준일_이전_스냅샷_정리를_리포지토리에_그대로_전달한다() {
        LocalDate cutoff = TODAY.minusDays(400);

        facilityGradeSnapshotWriter.deleteBefore(cutoff);

        verify(facilityGradeSnapshotRepository).deleteBySnapshotDateBefore(eq(cutoff));
    }

    private Facility facility() {
        Facility facility = Facility.builder()
                .name("테라로사 커피공장")
                .category(FacilityCategory.CAFE)
                .lat(new BigDecimal("37.7000000"))
                .lng(new BigDecimal("128.8000000"))
                .petAllowed(PetAllowed.ALLOWED)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", FACILITY_ID);
        ReflectionTestUtils.setField(facility, "petScore", 88.4);
        ReflectionTestUtils.setField(facility, "reviewCount", 96L);
        ReflectionTestUtils.setField(facility, "pawGradeLevel", 4);
        return facility;
    }
}
