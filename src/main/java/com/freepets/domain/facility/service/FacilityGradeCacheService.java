package com.freepets.domain.facility.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.repository.FacilityReviewAggregate;
import com.freepets.domain.review.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;

/**
 * 시설의 리뷰 집계 캐시(친화도 점수·리뷰 수·발자국 등급)를 갱신한다.
 *
 * <p>시설 상세 조회는 요청마다 그 시설 리뷰를 즉시 집계한다. 시설 한 곳이라 싸기 때문이다.
 * 반면 발자국 랭킹은 <b>전체 시설을 점수순으로 정렬</b>해야 해서, 같은 방식이라면 매 요청·매
 * 페이지마다 리뷰 테이블 전체를 group by 하게 된다. 그래서 랭킹이 읽는 값은 미리 시설에 저장해둔다.
 *
 * <p>리뷰는 자주 쓰이는 데이터가 아니므로 갱신 비용은 거의 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FacilityGradeCacheService {

    /** 백필이 한 번에 읽어올 시설 수. */
    private static final int BACKFILL_PAGE_SIZE = 500;

    private final FacilityRepository facilityRepository;
    private final ReviewRepository reviewRepository;

    /**
     * 시설 한 곳의 집계를 다시 계산해 저장한다. 리뷰가 바뀐 직후에 호출한다.
     *
     * <p>없는 시설이면 조용히 넘어간다. 이 호출은 리뷰 작성·삭제에 딸린 부수 작업이라, 시설이
     * 사라진 예외 상황 때문에 이미 성공한 리뷰 처리를 되돌릴 이유가 없다.
     */
    public void refresh(Long facilityId) {
        facilityRepository.findById(facilityId)
                .ifPresent(this::refresh);
    }

    /**
     * 저장된 리뷰 전체를 시설 캐시에 1회 반영한다. 캐시를 도입하기 전에 쌓인 리뷰를 채우는 용도다.
     *
     * <p>등급을 못 받은 시설까지 포함해 전 시설을 훑는다. 리뷰가 없는 시설은 값이 비워지므로,
     * 도중에 규칙이 바뀌어도 이 실행 한 번으로 전체가 같은 기준에 맞춰진다.
     *
     * @return 반영한 시설 수
     */
    public long refreshAll() {
        long refreshedCount = 0;
        Pageable pageable = Pageable.ofSize(BACKFILL_PAGE_SIZE);

        while (true) {
            Slice<Facility> facilities = facilityRepository.findAllBy(pageable);
            facilities.forEach(this::refresh);
            refreshedCount += facilities.getNumberOfElements();

            if (!facilities.hasNext()) {
                return refreshedCount;
            }

            // 처리해도 조회 조건이 달라지지 않으므로 다음 페이지로 직접 넘어간다.
            pageable = facilities.nextPageable();
        }
    }

    private void refresh(Facility facility) {
        FacilityReviewAggregate aggregate = reviewRepository
                .aggregateByFacilityId(facility.getFacilityId(), ReviewReportStatus.ACCEPTED)
                .orElse(null);

        facility.applyReviewAggregate(aggregate);
    }

}
