package com.freepets.domain.facility.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityGradeSnapshot;
import com.freepets.domain.facility.repository.FacilityGradeSnapshotRepository;
import com.freepets.domain.report.service.DenialReportNotificationService;

import lombok.RequiredArgsConstructor;

/**
 * {@link FacilityGradeSnapshotService}의 실제 쓰기 작업. 별도 빈으로 둔 이유는
 * {@link DenialReportNotificationService}와 같다 — 같은 클래스 안에서 이 메서드를 호출하면
 * 프록시를 거치지 않아 {@code @Transactional}이 걸리지 않는 셀프 인보케이션 문제가 있다.
 *
 * <p>페이지(최대 500건) 단위로만 트랜잭션을 연다. 전 시설을 하나의 트랜잭션으로 묶으면 영속성
 * 컨텍스트가 무한정 커지고, 한 페이지에서 실패했을 때 이미 커밋된 다른 페이지까지 함께 굴러갈
 * 이유가 없다.
 */
@Component
@RequiredArgsConstructor
public class FacilityGradeSnapshotWriter {

    private final FacilityGradeSnapshotRepository facilityGradeSnapshotRepository;

    /**
     * 이미 오늘 스냅샷이 있는 시설은 건너뛴다(스케줄러 재시작 등으로 같은 날 다시 불려도 중복 없음).
     *
     * @return 새로 적재한 시설 수
     */
    @Transactional
    public long snapshotPage(
            List<Facility> facilities,
            LocalDate today
    ) {
        long snapshotted = 0;
        for (Facility facility : facilities) {
            if (snapshot(facility, today)) {
                snapshotted++;
            }
        }
        return snapshotted;
    }

    /** 보관 기간이 지난 스냅샷 정리. */
    @Transactional
    public void deleteBefore(LocalDate cutoff) {
        facilityGradeSnapshotRepository.deleteBySnapshotDateBefore(cutoff);
    }

    private boolean snapshot(
            Facility facility,
            LocalDate today
    ) {
        if (facilityGradeSnapshotRepository.existsByFacility_FacilityIdAndSnapshotDate(facility.getFacilityId(), today)) {
            return false;
        }

        facilityGradeSnapshotRepository.save(
                FacilityGradeSnapshot.builder()
                        .facility(facility)
                        .snapshotDate(today)
                        .pawGradeLevel(facility.getPawGradeLevel())
                        .petScore(facility.getPetScore())
                        .reviewCount(facility.getReviewCount())
                        .build()
        );
        return true;
    }

}
