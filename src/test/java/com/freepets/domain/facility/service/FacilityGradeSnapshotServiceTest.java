package com.freepets.domain.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilityGradeSnapshot;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;

/**
 * 페이징 오케스트레이션만 검증한다. 실제 적재·정리 로직은 {@link FacilityGradeSnapshotWriter}가
 * 별도 빈으로 맡고 있어({@code FacilityGradeSnapshotWriterTest} 참고) 여기서는 그 위임이
 * 페이지마다 잘 일어나는지만 본다.
 */
@ExtendWith(MockitoExtension.class)
class FacilityGradeSnapshotServiceTest {

    private static final Long FACILITY_ID = 6L;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private FacilityGradeSnapshotWriter facilityGradeSnapshotWriter;

    @InjectMocks
    private FacilityGradeSnapshotService facilityGradeSnapshotService;

    @Test
    void snapshotAll_페이지마다_적재를_위임하고_적재_건수를_합산한다() {
        when(facilityRepository.findAllBy(any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(facility()), Pageable.ofSize(500), false));
        when(facilityGradeSnapshotWriter.snapshotPage(anyList(), any())).thenReturn(1L);

        long snapshotted = facilityGradeSnapshotService.snapshotAll();

        assertThat(snapshotted).isEqualTo(1L);
        ArgumentCaptor<LocalDate> today = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<List<Facility>> facilities = ArgumentCaptor.forClass(List.class);
        verify(facilityGradeSnapshotWriter).snapshotPage(facilities.capture(), today.capture());
        assertThat(facilities.getValue()).hasSize(1);
        assertThat(today.getValue()).isEqualTo(LocalDate.now(BUSINESS_ZONE));
    }

    /** 페이지가 여러 장이면 각 페이지가 따로 위임돼야 한다 — 페이지 하나가 실패해도 나머지는 남아야 하므로. */
    @Test
    void snapshotAll_다음_페이지가_있으면_이어서_위임한다() {
        SliceImpl<Facility> firstPage = new SliceImpl<>(List.of(facility()), Pageable.ofSize(1), true);
        SliceImpl<Facility> secondPage = new SliceImpl<>(List.of(facility()), Pageable.ofSize(1).withPage(1), false);
        when(facilityRepository.findAllBy(any(Pageable.class))).thenReturn(firstPage, secondPage);
        when(facilityGradeSnapshotWriter.snapshotPage(anyList(), any())).thenReturn(1L);

        long snapshotted = facilityGradeSnapshotService.snapshotAll();

        assertThat(snapshotted).isEqualTo(2L);
        verify(facilityGradeSnapshotWriter, times(2))
                .snapshotPage(anyList(), any());
    }

    /** 등급 추이 화면 기간(30일) + 여유분(370일) = 400일 이전을 정리 기준으로 넘겨야 한다. */
    @Test
    void snapshotAll_보관_기간이_지난_스냅샷_정리를_위임한다() {
        when(facilityRepository.findAllBy(any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(), Pageable.ofSize(500), false));

        facilityGradeSnapshotService.snapshotAll();

        ArgumentCaptor<LocalDate> cutoff = ArgumentCaptor.forClass(LocalDate.class);
        verify(facilityGradeSnapshotWriter).deleteBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(
                LocalDate.now(BUSINESS_ZONE).minusDays(FacilityGradeSnapshot.TREND_WINDOW_DAYS + 370)
        );
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
        return facility;
    }
}
