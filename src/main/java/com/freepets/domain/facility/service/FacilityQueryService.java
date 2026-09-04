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
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.repository.FacilityWithDistance;
import com.freepets.domain.facility.repository.RegionRepository;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.repository.FacilityReviewAggregate;
import com.freepets.domain.review.repository.FacilityReviewCount;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityQueryService {

    /** LIKE 패턴 문자를 사용자가 그대로 검색할 수 있도록 이스케이프 문자를 정한다. */
    private static final String LIKE_ESCAPE = "!";

    private final FacilityRepository facilityRepository;
    private final ReviewRepository reviewRepository;
    private final PetRepository petRepository;
    private final RegionRepository regionRepository;

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
     * 발자국 랭킹을 조회한다.
     *
     * <p>등급을 받은 시설만, 등급 → 점수 순으로 내려간다. 필터는 모두 선택이며 AND로 겹친다.
     *
     * <p>좌표는 선택이다. 위치 권한을 거부한 사용자도 랭킹은 볼 수 있어야 하므로, 좌표가 없으면
     * 거리를 계산하지 않고 {@code distanceM}만 비운다. 다만 좌표를 보낸 요청은 거리를 계산해야 해서
     * 좌표가 없는 시설이 결과에서 빠진다 — 목록 검색과 같은 규칙이다.
     */
    public FacilityResponseDTO.RankingResult getRanking(FacilityRequestDTO.RankingRequest request) {
        validateRankingRequest(request);

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());

        if (!request.isCoordinateGiven()) {
            List<Facility> found = facilityRepository.searchRanking(
                    request.getCategory(),
                    request.getPetAllowed(),
                    request.getSidoCode(),
                    request.getSigunguCode(),
                    pageable
            );
            long total = facilityRepository.countRanking(
                    request.getCategory(),
                    request.getPetAllowed(),
                    request.getSidoCode(),
                    request.getSigunguCode(),
                    false
            );

            return FacilityConverter.toRankingResult(
                    found,
                    Map.of(),
                    request.getPage(),
                    request.getSize(),
                    total
            );
        }

        double userLatitudeRadian = Math.toRadians(request.getLatitude());
        double userLongitudeRadian = Math.toRadians(request.getLongitude());

        List<FacilityWithDistance> found;
        long total;

        if (request.getRadiusM() == null) {
            found = facilityRepository.searchRankingWithDistance(
                    userLatitudeRadian,
                    userLongitudeRadian,
                    request.getCategory(),
                    request.getPetAllowed(),
                    request.getSidoCode(),
                    request.getSigunguCode(),
                    pageable
            );
            total = facilityRepository.countRanking(
                    request.getCategory(),
                    request.getPetAllowed(),
                    request.getSidoCode(),
                    request.getSigunguCode(),
                    true
            );
        } else {
            BoundingBox boundingBox = BoundingBox.around(
                    request.getLatitude(),
                    request.getLongitude(),
                    request.getRadiusM()
            );

            found = facilityRepository.searchRankingWithinRadius(
                    userLatitudeRadian,
                    userLongitudeRadian,
                    request.getCategory(),
                    request.getPetAllowed(),
                    request.getSidoCode(),
                    request.getSigunguCode(),
                    boundingBox.minimumLatitude(),
                    boundingBox.maximumLatitude(),
                    boundingBox.minimumLongitude(),
                    boundingBox.maximumLongitude(),
                    request.getRadiusM(),
                    pageable
            );
            total = facilityRepository.countRankingWithinRadius(
                    userLatitudeRadian,
                    userLongitudeRadian,
                    request.getCategory(),
                    request.getPetAllowed(),
                    request.getSidoCode(),
                    request.getSigunguCode(),
                    boundingBox.minimumLatitude(),
                    boundingBox.maximumLatitude(),
                    boundingBox.minimumLongitude(),
                    boundingBox.maximumLongitude(),
                    request.getRadiusM()
            );
        }

        return FacilityConverter.toRankingResult(
                found.stream().map(FacilityWithDistance::facility).toList(),
                distancesOf(found),
                request.getPage(),
                request.getSize(),
                total
        );
    }

    /**
     * 랭킹 화면의 지역 칩에 쓸 시도 → 시군구 목록을 조회한다.
     *
     * <p>관광공사 법정동 코드를 적재해둔 지역 테이블을 읽는다. 시설에서 지역을 역으로 모으지 않는
     * 이유는, 지역명이 시설 행마다 복사돼 있어 동기화가 중간에 끊기면 같은 코드에 옛 이름과 새 이름이
     * 섞이고 그러면 같은 지역의 칩이 두 개 나오기 때문이다.
     *
     * <p>등급 시설이 없는 지역도 함께 내려간다. 프론트는 이름을 칩으로 띄우고 클릭 시 코드를 랭킹
     * 조회에 실어 보내면 된다.
     *
     * <p>응답이 사용자마다 달라지지 않으므로 캐시 대상이다.
     */
    public List<FacilityResponseDTO.Region> getRegions() {
        return FacilityConverter.toRegions(regionRepository.findAllByOrderBySidoCodeAscSigunguCodeAsc());
    }

    /**
     * 애너테이션으로 표현할 수 없는 필드 간 관계를 검증한다.
     *
     * <p>조용히 무시하지 않고 400으로 알린다. 거리 필터를 걸었는데 반경이 무시된 목록을 받으면
     * 사용자는 필터가 먹은 줄 알게 된다.
     */
    private void validateRankingRequest(FacilityRequestDTO.RankingRequest request) {
        validateCoordinatePair(request.getLatitude(), request.getLongitude());

        if (request.getRadiusM() != null && !request.isCoordinateGiven()) {
            throw new GeneralException(
                    ErrorStatus.COMMON400,
                    Map.of("radiusM", "거리 필터를 쓰려면 위도와 경도가 필요합니다.")
            );
        }

        if (request.getSigunguCode() != null && request.getSidoCode() == null) {
            throw new GeneralException(
                    ErrorStatus.COMMON400,
                    Map.of("sigunguCode", "시군구 코드는 시도 코드와 함께 보내야 합니다.")
            );
        }
    }

    private Map<Long, Long> distancesOf(List<FacilityWithDistance> found) {
        return found.stream()
                .collect(Collectors.toMap(
                        facility -> facility.facility().getFacilityId(),
                        facility -> Math.round(facility.distanceMeter())
                ));
    }

    /**
     * 시설 상세를 조회한다.
     *
     * <p>리뷰 목록과 만족도 목록은 담지 않는다. 각각 전용 API가 있고, 페이징 단위와 갱신 주기가
     * 달라 한 응답에 묶으면 리뷰 한 건을 더 보려고 시설 정보까지 다시 받게 된다.
     *
     * @param latitude  사용자 위도. {@code null}이면 거리를 계산하지 않는다
     * @param longitude 사용자 경도. {@code null}이면 거리를 계산하지 않는다
     */
    public FacilityResponseDTO.FacilityDetail getFacilityDetail(
            Long facilityId,
            Long userId,
            Double latitude,
            Double longitude
    ) {
        validateCoordinatePair(latitude, longitude);

        Facility facility;
        Long distanceM;

        FacilityWithDistance found = latitude == null
                ? null
                : facilityRepository.findWithDistanceById(
                        Math.toRadians(latitude),
                        Math.toRadians(longitude),
                        facilityId
                ).orElse(null);

        if (found == null) {
            // 좌표를 안 보냈거나, 시설에 좌표가 없어 거리를 낼 수 없는 경우다.
            // 시설 자체가 없으면 여기서 404가 난다.
            facility = facilityRepository.findById(facilityId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4041));
            distanceM = null;
        } else {
            facility = found.facility();
            distanceM = Math.round(found.distanceMeter());
        }

        FacilityReviewAggregate aggregate = reviewRepository
                .aggregateByFacilityId(facilityId, ReviewReportStatus.ACCEPTED)
                .orElse(null);

        // 인증이 필요한 API라 userId는 항상 있다.
        List<Pet> myPets = petRepository.findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(userId);

        return FacilityConverter.toFacilityDetail(facility, distanceM, aggregate, myPets);
    }

    /**
     * 위도와 경도는 함께 와야 한다. 하나만 보내는 것은 클라이언트 실수이므로 조용히 거리를
     * 비우지 않고 400으로 알린다.
     */
    private void validateCoordinatePair(
            Double latitude,
            Double longitude
    ) {
        if (latitude == null && longitude == null) {
            return;
        }
        if (latitude != null && longitude != null) {
            return;
        }

        String missingField = latitude == null ? "latitude" : "longitude";
        throw new GeneralException(
                ErrorStatus.COMMON400,
                Map.of(missingField, "위도와 경도는 함께 보내야 합니다.")
        );
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
