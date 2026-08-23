package com.freepets.domain.facility.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.converter.FacilityConverter;
import com.freepets.domain.facility.dto.FacilityRequestDTO;
import com.freepets.domain.facility.dto.FacilityResponseDTO;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.repository.FacilityWithDistance;
import com.freepets.domain.review.repository.FacilityReviewCount;
import com.freepets.domain.review.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityQueryService {

    /** LIKE 패턴 문자를 사용자가 그대로 검색할 수 있도록 이스케이프 문자를 정한다. */
    private static final String LIKE_ESCAPE = "!";

    private final FacilityRepository facilityRepository;
    private final ReviewRepository reviewRepository;

    public FacilityResponseDTO.FacilitySearchResult searchFacilities(FacilityRequestDTO.SearchRequest request) {
        double userLatitudeRadian = Math.toRadians(request.getLatitude());
        double userLongitudeRadian = Math.toRadians(request.getLongitude());
        String keyword = toLikePattern(request.getKeyword());
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());

        List<FacilityWithDistance> found;
        long total;

        if (request.getRadiusM() == null) {
            found = facilityRepository.search(
                    userLatitudeRadian,
                    userLongitudeRadian,
                    keyword,
                    request.getCategory(),
                    request.getPetAllowed(),
                    pageable
            );
            total = facilityRepository.countSearch(
                    keyword,
                    request.getCategory(),
                    request.getPetAllowed()
            );
        } else {
            BoundingBox boundingBox = BoundingBox.around(
                    request.getLatitude(),
                    request.getLongitude(),
                    request.getRadiusM()
            );

            found = facilityRepository.searchWithinRadius(
                    userLatitudeRadian,
                    userLongitudeRadian,
                    keyword,
                    request.getCategory(),
                    request.getPetAllowed(),
                    boundingBox.minimumLatitude(),
                    boundingBox.maximumLatitude(),
                    boundingBox.minimumLongitude(),
                    boundingBox.maximumLongitude(),
                    request.getRadiusM(),
                    pageable
            );
            total = facilityRepository.countSearchWithinRadius(
                    userLatitudeRadian,
                    userLongitudeRadian,
                    keyword,
                    request.getCategory(),
                    request.getPetAllowed(),
                    boundingBox.minimumLatitude(),
                    boundingBox.maximumLatitude(),
                    boundingBox.minimumLongitude(),
                    boundingBox.maximumLongitude(),
                    request.getRadiusM()
            );
        }

        return FacilityConverter.toFacilitySearchResult(found, reviewCountsOf(found), total);
    }

    /**
     * 검색어를 부분 일치 패턴으로 바꾼다.
     *
     * <p>사용자가 입력한 {@code %}, {@code _}는 와일드카드가 아니라 글자 그대로 찾아야 하므로
     * 이스케이프한다. 이스케이프 문자 자신을 가장 먼저 처리해야 이중 치환되지 않는다.
     *
     * @return 검색어가 없으면 {@code null}. 쿼리에서 조건 자체를 건너뛰게 된다.
     */
    private String toLikePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String escaped = keyword.trim()
                .toLowerCase()
                .replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");

        return "%" + escaped + "%";
    }

    private Map<Long, Long> reviewCountsOf(List<FacilityWithDistance> found) {
        if (found.isEmpty()) {
            return Map.of();
        }

        List<Long> facilityIds = found.stream()
                .map(facility -> facility.facility().getFacilityId())
                .toList();

        return reviewRepository.countByFacilityIds(facilityIds).stream()
                .collect(Collectors.toMap(
                        FacilityReviewCount::facilityId,
                        FacilityReviewCount::reviewCount
                ));
    }
}
