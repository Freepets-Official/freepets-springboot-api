package com.freepets.domain.facility.service;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.converter.FacilityConverter;
import com.freepets.domain.facility.dto.FacilityRequestDTO;
import com.freepets.domain.facility.dto.FacilityResponseDTO;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.repository.RegionRepository;
import com.freepets.domain.review.repository.FacilityReviewCount;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.global.config.TourApiConfig;
import com.freepets.infra.tourapi.FacilityCategoryMapper;
import com.freepets.infra.tourapi.TourApiClient;
import com.freepets.infra.tourapi.TourApiException;
import com.freepets.infra.tourapi.TourApiResponseParser;
import com.freepets.infra.tourapi.dto.AreaBasedItem;

import lombok.extern.slf4j.Slf4j;

/**
 * 전체 시설 목록을 조회한다.
 *
 * <p>목록의 정본은 관광공사다. 조회 한 건이 관광공사 호출 한 건으로 남아야 해서, 우리 DB를 읽고
 * 마는 대신 요청마다 실제로 부른다. DB는 그 결과에 우리만 아는 정보(발자국 점수, 리뷰 수, 동반
 * 가능 여부)를 붙이는 데 쓴다.
 *
 * <p>다만 사업자가 직접 등록한 시설은 관광공사에 없으므로 DB에서 따로 가져와 합친다. 그러지 않으면
 * 사장님이 등록한 매장이 다른 화면에는 다 나오는데 이 목록에만 없게 된다.
 *
 * <p>조건에 맞는 전량을 한 응답에 받아 서버에서 거르고 자른다. 관광공사에서 페이지를 나눠 받으면
 * 동반 가능 필터를 건 뒤 페이지마다 남는 건수가 들쭉날쭉해지고, 관광공사가 주는 총 건수는 필터
 * 이전 값이라 마지막 페이지가 맞지 않는다. 시군구를 필수로 받는 것이 이 방식의 전제다
 * (시군구 단위 최대 700여 건, 시도는 9천 건이 넘는다).
 *
 * <p>{@link FacilityQueryService}와 나눈 이유는 관심사다. 그쪽은 순수 DB 조회이고, 이쪽은 외부
 * 호출과 그 실패에 대한 폴백을 안고 있다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class FacilityListQueryService {

    /**
     * 관광공사에서 한 번에 받아올 최대 건수.
     *
     * <p>관측된 시군구 단위 최대가 700여 건(경기 파주 736)이라 두 배 여유를 뒀다. 관광공사는
     * {@code numOfRows}에 상한을 두지 않아 이 값이 곧 우리가 감당하기로 한 응답 크기다.
     */
    private static final int MAXIMUM_FETCH_ROWS = 1500;

    /** 전량을 한 번에 받으므로 언제나 첫 페이지만 요청한다. */
    private static final int FIRST_PAGE = 1;

    private final TourApiClient tourApiClient;
    private final TourApiResponseParser tourApiResponseParser;
    private final FacilityCategoryMapper facilityCategoryMapper;
    private final FacilityRepository facilityRepository;
    private final ReviewRepository reviewRepository;
    private final RegionRepository regionRepository;

    /**
     * 배치용이 아니라 요청 경로용 클라이언트를 받는다. 배치용은 호출 사이에 250ms를 쉬어서
     * 동시에 들어온 요청들이 서로를 기다리게 된다({@link TourApiConfig} 참고).
     */
    public FacilityListQueryService(
            @Qualifier(TourApiConfig.ON_DEMAND_CLIENT) TourApiClient tourApiClient,
            TourApiResponseParser tourApiResponseParser,
            FacilityCategoryMapper facilityCategoryMapper,
            FacilityRepository facilityRepository,
            ReviewRepository reviewRepository,
            RegionRepository regionRepository
    ) {
        this.tourApiClient = tourApiClient;
        this.tourApiResponseParser = tourApiResponseParser;
        this.facilityCategoryMapper = facilityCategoryMapper;
        this.facilityRepository = facilityRepository;
        this.reviewRepository = reviewRepository;
        this.regionRepository = regionRepository;
    }

    public FacilityResponseDTO.FacilityListResult getFacilityList(FacilityRequestDTO.FacilityListRequest request) {
        validateRegion(request);

        List<AreaBasedItem> fetched;
        try {
            fetched = fetchFromTourApi(request);
        } catch (TourApiException exception) {
            // 관광공사가 죽었다고 목록 화면까지 죽을 이유는 없다. 적재해둔 데이터로 대신 답한다.
            log.warn("관광공사 조회에 실패해 DB로 대신 응답합니다. sidoCode={}, sigunguCode={}",
                    request.getSidoCode(), request.getSigunguCode(), exception);
            return findFromDatabase(request);
        }

        // 관광공사에서 찾은 시설과 관광공사에 없는 시설을 합친 뒤에 정렬한다. 각각 정렬해 이어붙이면
        // 자체 등록 시설이 목록 끝에 뭉쳐 나온다.
        List<Facility> merged = Stream.concat(
                        matchToFacilities(fetched, request).stream(),
                        findWithoutContentId(request).stream())
                .sorted(byName())
                .toList();

        List<Facility> pageSlice = slice(merged, request.getPage(), request.getSize());

        return FacilityConverter.toFacilityListResult(pageSlice, reviewCountsOf(pageSlice), merged.size());
    }

    /**
     * 지역 조건을 검증한다.
     *
     * <p>시군구는 필수다. 다만 하위 시군구 행이 없는 시도는 비울 수 있어야 하므로, 그 시도에
     * 시군구가 실재하는지를 보고 판단한다. 관광공사 법정동 코드에서는 세종특별자치시조차
     * 시군구 코드를 시도와 같은 {@code 36110}으로 내려주므로, 지금 데이터에서는 모든 시도가
     * 시군구를 갖는다. 코드 체계가 바뀔 때를 위해 조건으로 남긴다.
     *
     * <p>없는 코드를 조용히 넘기지 않는다. 관광공사는 모르는 코드에 빈 목록을 주는데, 그러면
     * 사용자는 "그 지역에 시설이 없다"로 읽게 된다.
     */
    private void validateRegion(FacilityRequestDTO.FacilityListRequest request) {
        String sigunguCode = emptyToNull(request.getSigunguCode());

        if (sigunguCode == null && regionRepository.existsBySidoCodeAndSigunguCodeIsNotNull(request.getSidoCode())) {
            throw new GeneralException(
                    ErrorStatus.COMMON400,
                    Map.of("sigunguCode", "시군구 코드는 필수입니다.")
            );
        }

        if (regionRepository.findBySidoCodeAndSigunguCode(request.getSidoCode(), sigunguCode).isEmpty()) {
            throw new GeneralException(
                    ErrorStatus.COMMON400,
                    Map.of("sidoCode", "존재하지 않는 지역입니다.")
            );
        }
    }

    /**
     * 조건에 맞는 시설을 관광공사에서 한 번에 받아온다.
     *
     * <p>카페는 음식점과 같은 {@code contentTypeId=39}를 쓰므로 분류체계 중분류까지 실어 좁힌다.
     * 반대로 음식점은 "카페가 아닌 39"라 관광공사에서 못 거르고 받은 뒤에 걷어낸다.
     */
    private List<AreaBasedItem> fetchFromTourApi(FacilityRequestDTO.FacilityListRequest request) {
        String body = tourApiClient.areaBasedList(
                facilityCategoryMapper.toContentTypeId(request.getCategory()),
                request.getSidoCode(),
                emptyToNull(request.getSigunguCode()),
                facilityCategoryMapper.toMediumCategoryCode(request.getCategory()),
                FIRST_PAGE,
                MAXIMUM_FETCH_ROWS
        );

        int totalCount = tourApiResponseParser.parseTotalCount(body);
        if (totalCount > MAXIMUM_FETCH_ROWS) {
            // 여기 걸리면 뒷부분이 잘려 나간다. 한 번에 받는 전제가 깨진 것이니 상한을 다시 봐야 한다.
            log.warn("조건에 맞는 시설이 한 번에 받을 수 있는 수를 넘었습니다. sidoCode={}, sigunguCode={}, "
                            + "category={}, totalCount={}, 상한={}",
                    request.getSidoCode(), request.getSigunguCode(), request.getCategory(),
                    totalCount, MAXIMUM_FETCH_ROWS);
        }

        return tourApiResponseParser.parseItems(body, AreaBasedItem.class);
    }

    /**
     * 관광공사 응답을 우리 시설로 옮긴다.
     *
     * <p>DB에 없는 {@code contentId}는 뺀다. 적재 이후 관광공사에 새로 올라온 시설이거나 우리가
     * 적재 대상으로 보지 않는 분류라, 내려보내도 상세를 열 수 없다.
     */
    private List<Facility> matchToFacilities(
            List<AreaBasedItem> fetched,
            FacilityRequestDTO.FacilityListRequest request
    ) {
        List<String> contentIds = fetched.stream()
                .filter(item -> !facilityCategoryMapper.isExcludedFrom(request.getCategory(), item.mediumCategoryCode()))
                .map(AreaBasedItem::contentId)
                .filter(Objects::nonNull)
                .toList();

        if (contentIds.isEmpty()) {
            return List.of();
        }

        List<Facility> found = facilityRepository.findByContentIdIn(contentIds);

        int missingCount = contentIds.size() - found.size();
        if (missingCount > 0) {
            // 꾸준히 커지면 적재 주기가 관광공사 갱신을 못 따라가고 있다는 신호다.
            log.info("관광공사에는 있으나 적재되지 않은 시설을 {}건 건너뛰었습니다. sidoCode={}, sigunguCode={}",
                    missingCount, request.getSidoCode(), request.getSigunguCode());
        }

        return found.stream()
                .filter(Facility::isActive)
                .filter(facility -> request.getPetAllowed() == null
                        || facility.getPetAllowed() == request.getPetAllowed())
                .toList();
    }

    /**
     * 관광공사에 없는 시설을 DB에서 따로 가져온다.
     *
     * <p>사업자가 직접 등록한 시설은 관광공사 응답에 없으니 콘텐츠 ID로는 영영 찾히지 않는다.
     * 이 목록에서만 빠지면 사장님은 자기 매장이 검색·랭킹·상세에는 나오는데 전체 목록에만 없는
     * 상태를 보게 되고, 관광공사가 죽어 폴백으로 넘어갔을 때만 나타나게 된다.
     *
     * <p>지역·분류·동반 여부 필터는 쿼리가 건다. 관광공사 응답을 거를 때와 같은 조건이다.
     */
    private List<Facility> findWithoutContentId(FacilityRequestDTO.FacilityListRequest request) {
        return facilityRepository.findAllWithoutContentId(
                request.getCategory(),
                request.getPetAllowed(),
                request.getSidoCode(),
                emptyToNull(request.getSigunguCode())
        );
    }

    /**
     * 이름 가나다순 비교자.
     *
     * <p>{@code String.compareTo}는 유니코드 코드포인트 순이라 한글 정렬이 어긋난다.
     * {@link Collator}는 스레드 안전하지 않으므로 호출마다 새로 만든다 — 요청당 한 번이라 부담이 없다.
     */
    private Comparator<Facility> byName() {
        Collator collator = Collator.getInstance(Locale.KOREAN);

        return Comparator.comparing(Facility::getName, collator::compare)
                .thenComparing(Facility::getFacilityId);
    }

    /**
     * 걸러낸 목록에서 한 페이지를 잘라낸다.
     *
     * <p>{@code page * size}를 {@code long}으로 계산한다. 큰 페이지 번호가 들어오면 int 곱셈이
     * 넘쳐 음수가 되고, 그러면 빈 목록 대신 첫 페이지가 나간다.
     */
    private List<Facility> slice(
            List<Facility> facilities,
            int page,
            int size
    ) {
        long fromIndex = (long) page * size;
        if (fromIndex >= facilities.size()) {
            return List.of();
        }

        int toIndex = (int) Math.min(fromIndex + size, facilities.size());
        return facilities.subList((int) fromIndex, toIndex);
    }

    /**
     * 관광공사를 못 쓸 때 적재해둔 데이터로 같은 형태의 응답을 만든다.
     *
     * <p>정렬 기준이 DB 컬레이션이라 관광공사 경로와 순서가 미세하게 다를 수 있다. 장애 중에만
     * 도는 경로라 그 차이를 맞추려고 전 건을 메모리로 올리지는 않는다.
     */
    private FacilityResponseDTO.FacilityListResult findFromDatabase(FacilityRequestDTO.FacilityListRequest request) {
        String sigunguCode = emptyToNull(request.getSigunguCode());
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());

        List<Facility> found = facilityRepository.searchAll(
                request.getCategory(),
                request.getPetAllowed(),
                request.getSidoCode(),
                sigunguCode,
                pageable
        );
        long total = facilityRepository.countAll(
                request.getCategory(),
                request.getPetAllowed(),
                request.getSidoCode(),
                sigunguCode
        );

        return FacilityConverter.toFacilityListResult(found, reviewCountsOf(found), total);
    }

    private Map<Long, Long> reviewCountsOf(List<Facility> facilities) {
        if (facilities.isEmpty()) {
            return Map.of();
        }

        List<Long> facilityIds = facilities.stream()
                .map(Facility::getFacilityId)
                .toList();

        return reviewRepository.countByFacilityIds(facilityIds).stream()
                .collect(Collectors.toMap(
                        FacilityReviewCount::facilityId,
                        FacilityReviewCount::reviewCount
                ));
    }

    /** 빈 문자열로 온 선택 파라미터를 "안 보냈다"와 같게 다룬다. */
    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

}
