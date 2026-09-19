package com.freepets.domain.facility.service;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.converter.FacilityConverter;
import com.freepets.domain.facility.dto.FacilityRequestDTO;
import com.freepets.domain.facility.dto.FacilityResponseDTO;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.review.repository.FacilityReviewCount;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.infra.tourapi.FacilityCategoryMapper;
import com.freepets.infra.tourapi.dto.AreaBasedItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 전체 시설 목록의 DB 작업을 맡는다. 관광공사가 준 것에 우리가 아는 것을 붙여 응답을 완성한다.
 *
 * <p>{@link FacilityListQueryService}에서 떼어낸 이유는 트랜잭션 경계 때문이다. 한 클래스에 두면
 * 관광공사 호출까지 트랜잭션 안에 들어간다. 그 호출은 최대 30초까지 블로킹되는데, 그동안 DB 커넥션을
 * 쥐고 있으면 인증 없이 열린 이 조회로 들어온 요청들이 커넥션 풀을 말려 앱 전체를 멈출 수 있다.
 * 같은 클래스 안에서 부르면 스프링 프록시를 타지 않아 메소드에 애너테이션을 붙이는 것으로는 안 되고,
 * {@code FacilityUpsertService}·{@code DenialReportNotificationService}와 같은 이유로 클래스를 나눈다.
 *
 * <p>변환까지 여기서 끝내는 것도 같은 이유다. {@code FacilityConverter}가 체크리스트 같은 지연 로딩
 * 연관을 읽으므로 영속성 컨텍스트가 살아 있는 동안 끝나야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityListAssembler {

    private final FacilityRepository facilityRepository;
    private final ReviewRepository reviewRepository;
    private final FacilityCategoryMapper facilityCategoryMapper;

    /**
     * 관광공사 응답에 우리 데이터를 붙여 한 페이지를 만든다.
     *
     * @param fetched 관광공사에서 받은 조건에 맞는 전량
     */
    public FacilityResponseDTO.FacilityListResult assemble(
            List<AreaBasedItem> fetched,
            FacilityRequestDTO.FacilityListRequest request
    ) {
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
     * 관광공사를 못 쓸 때 적재해둔 데이터로 같은 형태의 응답을 만든다.
     *
     * <p>정렬 기준이 DB 컬레이션이라 관광공사 경로와 순서가 미세하게 다를 수 있다. 장애 중에만
     * 도는 경로라 그 차이를 맞추려고 전 건을 메모리로 올리지는 않는다.
     */
    public FacilityResponseDTO.FacilityListResult assembleFromDatabase(FacilityRequestDTO.FacilityListRequest request) {
        String sigunguCode = request.sigunguCodeOrNull();
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
                request.sigunguCodeOrNull()
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

}
