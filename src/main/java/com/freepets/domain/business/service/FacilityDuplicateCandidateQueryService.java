package com.freepets.domain.business.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.business.converter.BusinessConverter;
import com.freepets.domain.business.dto.BusinessResponseDTO;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.repository.FacilityWithDistance;
import com.freepets.domain.facility.service.BoundingBox;
import com.freepets.domain.facility.service.FacilityNameMatcher;

import lombok.RequiredArgsConstructor;

/**
 * 신규 매장 등록 전 중복 후보 조회. 사전조회 API({@code BusinessQueryService.duplicateCheck})와 등록
 * API의 방어적 재확인({@code BusinessCommandService.registerFacility}) 양쪽이 이 서비스를 공유한다 —
 * 앱이 사전조회를 건너뛰어도 등록 시점에 같은 검사가 걸리게 하기 위해서다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityDuplicateCandidateQueryService {

    /** 이슈가 지정한 확인 반경. */
    private static final int DUPLICATE_RADIUS_METER = 100;

    /** 후보가 너무 많으면 화면이 감당하기 어려워 상위 몇 건만 보여준다. */
    private static final int MAX_CANDIDATES = 5;

    /**
     * {@code searchWithinRadius}가 반경 안 전체 시설을 후보로 넘겨준 뒤, 실제로 화면에 보여줄 후보보다
     * 넉넉히 뽑아 이름 유사도로 한 번 더 거른다. 반경 안 시설이 이 값보다 많아 진짜 후보를 놓치는 상황은
     * 매장 밀집 지역에서도 드물다.
     */
    private static final int CANDIDATE_POOL_SIZE = 20;

    private final FacilityRepository facilityRepository;

    public List<BusinessResponseDTO.FacilityDuplicateCandidate> findCandidates(
            String name,
            double lat,
            double lng
    ) {
        BoundingBox boundingBox = BoundingBox.around(lat, lng, DUPLICATE_RADIUS_METER);

        List<FacilityWithDistance> nearby = facilityRepository.searchWithinRadius(
                Math.toRadians(lat),
                Math.toRadians(lng),
                null,
                null,
                null,
                boundingBox.minimumLatitude(),
                boundingBox.maximumLatitude(),
                boundingBox.minimumLongitude(),
                boundingBox.maximumLongitude(),
                DUPLICATE_RADIUS_METER,
                PageRequest.of(0, CANDIDATE_POOL_SIZE)
        );

        return nearby.stream()
                .filter(candidate -> FacilityNameMatcher.isSimilar(candidate.facility().getName(), name))
                .limit(MAX_CANDIDATES)
                .map(BusinessConverter::toFacilityDuplicateCandidate)
                .toList();
    }
}
