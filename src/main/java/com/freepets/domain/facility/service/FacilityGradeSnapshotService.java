package com.freepets.domain.facility.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityGradeSnapshot;
import com.freepets.domain.facility.repository.FacilityRepository;

import lombok.RequiredArgsConstructor;

/**
 * 발자국 등급 추이용 스냅샷 적재. {@link FacilityGradeSnapshotScheduler}가 매일 한 번 호출한다.
 *
 * <p>{@code Facility.pawGradeLevel}/{@code petScore}는 현재값만 들고 있어, 추이를 그리려면
 * 그 시점 값을 별도 테이블에 쌓아야 한다({@link FacilityGradeSnapshot}).
 *
 * <p>이 클래스 자체는 트랜잭션을 열지 않는다 — 전 시설 페이징만 담당하고, 실제 쓰기는
 * {@link FacilityGradeSnapshotWriter}에 페이지 단위로 위임한다(그 클래스의 주석 참고).
 */
@Service
@RequiredArgsConstructor
public class FacilityGradeSnapshotService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final int BACKFILL_PAGE_SIZE = 500;
    /**
     * 등급 추이 화면이 보장하는 기간({@link FacilityGradeSnapshot#TREND_WINDOW_DAYS})보다 넉넉히
     * 잡아둔 여유분. 보관 기간을 "화면이 필요로 하는 기간 + 여유분"으로 계산해, 추이 조회 기간이
     * 나중에 늘어나도 보관 기간이 따로 놀지 않는다.
     */
    private static final long RETENTION_BUFFER_DAYS = 370;

    private final FacilityRepository facilityRepository;
    private final FacilityGradeSnapshotWriter facilityGradeSnapshotWriter;

    /**
     * 전 시설의 현재 등급·점수·리뷰 수를 오늘 날짜로 스냅샷 적재한다. 같은 날 다시 불려도(재시작 등)
     * 이미 오늘 스냅샷이 있는 시설은 건너뛴다.
     *
     * <p>이어서 보관 기간이 지난 스냅샷을 정리한다 — 매일 쌓이기만 하면 테이블이 무한정 커진다.
     *
     * @return 새로 적재한 시설 수
     */
    public long snapshotAll() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        long snapshotted = 0;
        Pageable pageable = Pageable.ofSize(BACKFILL_PAGE_SIZE);

        while (true) {
            Slice<Facility> facilities = facilityRepository.findAllBy(pageable);
            snapshotted += facilityGradeSnapshotWriter.snapshotPage(facilities.getContent(), today);

            if (!facilities.hasNext()) {
                break;
            }
            pageable = facilities.nextPageable();
        }

        facilityGradeSnapshotWriter.deleteBefore(today.minusDays(FacilityGradeSnapshot.TREND_WINDOW_DAYS + RETENTION_BUFFER_DAYS));
        return snapshotted;
    }

}
