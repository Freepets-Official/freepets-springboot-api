package com.freepets.domain.facility.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.freepets.domain.facility.dto.FacilityRequestDTO;
import com.freepets.domain.facility.dto.FacilityResponseDTO;
import com.freepets.domain.facility.repository.RegionRepository;
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
 * <p>시군구까지 좁힌 조회에서는 관광공사가 목록의 정본이다. 조회 한 건이 관광공사 호출 한 건으로
 * 남아야 해서, 우리 DB를 읽고 마는 대신 요청마다 실제로 부른다. DB는 그 결과에 우리만 아는
 * 정보(발자국 점수, 리뷰 수, 동반 가능 여부)를 붙이는 데 쓴다 — 그쪽은
 * {@link FacilityListAssembler}가 맡는다.
 *
 * <p>조건에 맞는 전량을 한 응답에 받아 서버에서 거르고 자른다. 관광공사에서 페이지를 나눠 받으면
 * 동반 가능 필터를 건 뒤 페이지마다 남는 건수가 들쭉날쭉해지고, 관광공사가 주는 총 건수는 필터
 * 이전 값이라 마지막 페이지가 맞지 않는다. 시군구까지 좁히는 것이 이 방식의 전제다(시군구 단위
 * 최대 700여 건). 그보다 넓은 범위 — 시군구를 비운 시도 단위(9천 건 이상)와 전국(5만 건에 가까움)
 * — 는 이 전제가 깨지므로 적재해둔 DB에서 내려간다.
 *
 * <p>이 클래스에는 트랜잭션을 걸지 않는다. 관광공사 호출이 최대 30초까지 블로킹되는데 그동안
 * DB 커넥션을 쥐고 있으면 안 되기 때문이다. DB 작업은 모두 {@link FacilityListAssembler} 안에서
 * 끝난다. 지역 검증은 짧은 단건 조회 둘이라 리포지토리 자체 트랜잭션으로 충분하다.
 */
@Slf4j
@Service
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
    private final RegionRepository regionRepository;
    private final FacilityListAssembler facilityListAssembler;

    /**
     * 배치용이 아니라 요청 경로용 클라이언트를 받는다. 배치용은 호출 사이에 250ms를 쉬어서
     * 동시에 들어온 요청들이 서로를 기다리게 된다({@link TourApiConfig} 참고).
     */
    public FacilityListQueryService(
            @Qualifier(TourApiConfig.ON_DEMAND_CLIENT) TourApiClient tourApiClient,
            TourApiResponseParser tourApiResponseParser,
            FacilityCategoryMapper facilityCategoryMapper,
            RegionRepository regionRepository,
            FacilityListAssembler facilityListAssembler
    ) {
        this.tourApiClient = tourApiClient;
        this.tourApiResponseParser = tourApiResponseParser;
        this.facilityCategoryMapper = facilityCategoryMapper;
        this.regionRepository = regionRepository;
        this.facilityListAssembler = facilityListAssembler;
    }

    public FacilityResponseDTO.FacilityListResult getFacilityList(FacilityRequestDTO.FacilityListRequest request) {
        validateRegion(request);

        // 관광공사 실시간 경로는 시군구까지 좁혔을 때만 쓸 수 있다. 전량을 한 번에 받는 구조라
        // 시도 단위(9천 건)·전국(5만 건, 30MB)은 상한을 넘고, 나눠 받으면 동반 가능 필터를 건 뒤
        // 페이지마다 남는 건수가 들쭉날쭉해진다. 그 둘은 적재해둔 데이터로 답한다.
        if (request.sigunguCodeOrNull() == null) {
            // sidoCode가 비어 있으면 전국, 값이 있으면 시도 단위다 — 관광공사를 부르지 않은
            // 이유를 운영 로그에서 그대로 구분할 수 있게 둘 다 남긴다(#156).
            log.info("시군구를 좁히지 않은 조회라 관광공사를 부르지 않고 DB로 응답합니다. "
                    + "sidoCode={}, category={}", request.getSidoCode(), request.getCategory());
            return facilityListAssembler.assembleFromDatabase(request);
        }

        List<AreaBasedItem> fetched;
        try {
            fetched = fetchFromTourApi(request);
        } catch (TourApiException exception) {
            // 관광공사가 죽었다고 목록 화면까지 죽을 이유는 없다. 적재해둔 데이터로 대신 답한다.
            log.warn("관광공사 조회에 실패해 DB로 대신 응답합니다. sidoCode={}, sigunguCode={}",
                    request.getSidoCode(), request.getSigunguCode(), exception);
            return facilityListAssembler.assembleFromDatabase(request);
        }

        return facilityListAssembler.assemble(fetched, request);
    }

    /**
     * 지역 조건을 검증한다.
     *
     * <p>시군구는 선택이다(#154) — 비우면 시도 전체를 본다. 다만 없는 코드를 조용히 넘기지는
     * 않는다. 관광공사도 우리 DB도 모르는 코드에는 빈 목록을 주는데, 그러면 사용자는 "그 지역에
     * 시설이 없다"로 읽게 된다.
     */
    private void validateRegion(FacilityRequestDTO.FacilityListRequest request) {
        String sigunguCode = request.sigunguCodeOrNull();

        if (request.isNationwide()) {
            // 시도 없이 시군구만 온 경우다. 시군구 코드는 시도 안에서만 유일해서 단독으로는
            // 가리키는 지역이 정해지지 않는다. 조용히 전국을 내려주면 필터가 먹은 줄 알게 된다.
            if (sigunguCode != null) {
                throw new GeneralException(
                        ErrorStatus.COMMON400,
                        Map.of("sidoCode", "시군구 코드는 시도 코드와 함께 보내야 합니다.")
                );
            }
            return;
        }

        if (sigunguCode == null) {
            // 시도 단위 — 시군구 행이 있든 없든(세종처럼) 시도 자체가 실재하기만 하면 된다.
            if (!regionRepository.existsBySidoCode(request.getSidoCode())) {
                throw new GeneralException(
                        ErrorStatus.COMMON400,
                        Map.of("sidoCode", "존재하지 않는 지역입니다.")
                );
            }
            return;
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
        long startedAtMillis = System.currentTimeMillis();
        String body = tourApiClient.areaBasedList(
                facilityCategoryMapper.toContentTypeId(request.getCategory()),
                request.getSidoCode(),
                request.sigunguCodeOrNull(),
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

        List<AreaBasedItem> items = tourApiResponseParser.parseItems(body, AreaBasedItem.class);

        // 목록이 정말 관광공사에서 왔는지 운영 로그로 확인할 수 있게 남긴다.
        // totalCount는 관광공사 응답 본문에서만 나오는 값이라 DB 대체 응답과 구분된다.
        log.info("관광공사에서 시설 목록을 받았습니다. sidoCode={}, sigunguCode={}, category={}, "
                        + "totalCount={}, 수신={}건, 응답크기={}자, 소요={}ms",
                request.getSidoCode(), request.getSigunguCode(), request.getCategory(),
                totalCount, items.size(), body.length(), System.currentTimeMillis() - startedAtMillis);

        return items;
    }

}
