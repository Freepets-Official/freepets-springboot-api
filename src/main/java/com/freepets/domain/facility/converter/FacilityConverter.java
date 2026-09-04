package com.freepets.domain.facility.converter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.freepets.domain.facility.dto.FacilityResponseDTO;
import com.freepets.domain.facility.entity.CheckList;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.PetFriendlyGrade;
import com.freepets.domain.facility.entity.Region;
import com.freepets.domain.facility.entity.Requirement;
import com.freepets.domain.facility.repository.FacilityWithDistance;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.review.repository.FacilityReviewAggregate;
import com.freepets.global.util.Numbers;

public class FacilityConverter {

    private FacilityConverter() {}

    public static FacilityResponseDTO.FacilitySummary toFacilitySummary(
            FacilityWithDistance facilityWithDistance,
            long reviewCount
    ) {
        Facility facility = facilityWithDistance.facility();

        List<Requirement> requirements = facility.getCheckLists().stream()
                .map(CheckList::getType)
                .toList();

        return new FacilityResponseDTO.FacilitySummary(
                facility.getFacilityId(),
                facility.getName(),
                facility.getCategory(),
                facility.getAddress(),
                Math.round(facilityWithDistance.distanceMeter()),
                facility.getPetAllowed(),
                facility.getMaxWeight(),
                requirements,
                toDisplayScore(facility.getPetScore()),
                PetFriendlyGrade.labelOf(facility.getPetScore(), reviewCount),
                reviewCount
        );
    }

    /**
     * 목록 요약의 점수는 정수로 내린다. 화면이 "87점"처럼 보여주기 때문이다.
     *
     * <p>등급 판정에는 이 값을 쓰지 않는다. 87.96이 88이 되며 한 등급 올라가면 안 되므로,
     * 판정은 언제나 반올림 전 원점수로 한다.
     */
    private static Integer toDisplayScore(Double petScore) {
        return petScore == null ? null : (int) Math.round(petScore);
    }

    /**
     * 발자국 랭킹 목록을 만든다.
     *
     * <p>{@code rank}는 페이지를 가로질러 이어져야 하므로 현재 페이지 위치에서 시작한다.
     *
     * @param distances 시설 ID → 사용자 위치로부터의 거리(m). 좌표를 안 보냈으면 빈 맵이다
     * @param page      0부터 시작하는 페이지 번호
     * @param size      페이지 크기
     * @param total     페이징 이전, 조건에 맞는 전체 시설 수
     */
    public static FacilityResponseDTO.RankingResult toRankingResult(
            List<Facility> facilities,
            Map<Long, Long> distances,
            int page,
            int size,
            long total
    ) {
        int firstRank = page * size + 1;

        List<FacilityResponseDTO.RankingItem> items = IntStream.range(0, facilities.size())
                .mapToObj(index -> toRankingItem(
                        facilities.get(index),
                        distances.get(facilities.get(index).getFacilityId()),
                        firstRank + index
                ))
                .toList();

        return new FacilityResponseDTO.RankingResult(items, total);
    }

    private static FacilityResponseDTO.RankingItem toRankingItem(
            Facility facility,
            Long distanceM,
            int rank
    ) {
        // 정렬에 쓴 값을 그대로 보여준다. 점수로 다시 판정하면 정렬 순서와 배지가 어긋날 수 있다.
        //
        // 등급이 있으면 점수도 반드시 있다(applyReviewAggregate가 둘을 함께 채운다). 랭킹 쿼리는
        // 등급을 받은 시설만 뽑으므로 아래 점수 언박싱은 안전하다.
        PetFriendlyGrade grade = PetFriendlyGrade.ofLevel(facility.getPawGradeLevel());

        return new FacilityResponseDTO.RankingItem(
                rank,
                facility.getFacilityId(),
                facility.getName(),
                facility.getCategory(),
                facility.getSido(),
                facility.getSigungu(),
                distanceM,
                facility.getPetAllowed(),
                new FacilityResponseDTO.PawGrade(
                        PetFriendlyGrade.levelOf(grade),
                        PetFriendlyGrade.displayLabelOf(grade, facility.getReviewCount())
                ),
                Numbers.roundToOneDecimal(facility.getPetScore()),
                facility.getReviewCount()
        );
    }

    /**
     * @param reviewCounts 시설 ID → 리뷰 수. 리뷰가 없는 시설은 담겨 있지 않으므로 0으로 채운다.
     * @param total 페이징 이전, 조건에 맞는 전체 시설 수
     */
    public static FacilityResponseDTO.FacilitySearchResult toFacilitySearchResult(
            List<FacilityWithDistance> facilities,
            Map<Long, Long> reviewCounts,
            long total
    ) {
        List<FacilityResponseDTO.FacilitySummary> items = facilities.stream()
                .map(facility -> toFacilitySummary(
                        facility,
                        reviewCounts.getOrDefault(facility.facility().getFacilityId(), 0L)
                ))
                .toList();

        return new FacilityResponseDTO.FacilitySearchResult(items, total);
    }

    /**
     * 지역 목록을 시도 → 시군구 2단계 칩 구조로 접는다.
     *
     * <p>입력은 행정구역 코드 순으로 정렬돼 온다. 그 순서를 그대로 유지해야 하므로 기본
     * {@code HashMap}이 아니라 {@link LinkedHashMap}으로 묶는다. 기본 맵은 넣은 순서를 지키지 않아
     * 정렬이 깨진다.
     *
     * <p>등급 시설이 없는 지역도 그대로 내려간다. 사용자가 "여긴 아직 없구나"를 아는 편이
     * 칩이 통째로 사라지는 것보다 덜 헷갈린다.
     */
    public static List<FacilityResponseDTO.Region> toRegions(List<Region> regions) {
        Map<String, List<Region>> bySidoCode = regions.stream()
                .collect(Collectors.groupingBy(
                        Region::getSidoCode,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return bySidoCode.values().stream()
                .map(FacilityConverter::toRegion)
                .toList();
    }

    /** 시군구가 없는 시도(세종특별자치시 등)는 하위 칩 없이 시도 칩만 만든다. */
    private static FacilityResponseDTO.Region toRegion(List<Region> sidoRegions) {
        Region first = sidoRegions.get(0);

        List<FacilityResponseDTO.Sigungu> sigungus = sidoRegions.stream()
                .filter(Region::hasSigungu)
                .map(region -> new FacilityResponseDTO.Sigungu(
                        region.getSigunguCode(),
                        region.getSigungu()
                ))
                .toList();

        return new FacilityResponseDTO.Region(first.getSidoCode(), first.getSido(), sigungus);
    }

    /**
     * 시설 상세 응답을 만든다.
     *
     * @param distanceM 요청에 좌표가 없거나 시설에 좌표가 없으면 {@code null}
     * @param aggregate 적격 리뷰가 한 건도 없으면 {@code null}
     * @param myPets 조회한 사용자의 반려동물. 없으면 빈 목록
     */
    public static FacilityResponseDTO.FacilityDetail toFacilityDetail(
            Facility facility,
            Long distanceM,
            FacilityReviewAggregate aggregate,
            List<Pet> myPets
    ) {
        return new FacilityResponseDTO.FacilityDetail(
                facility.getFacilityId(),
                facility.getName(),
                facility.getCategory(),
                facility.getAddress(),
                facility.getPhone(),
                toCoordinate(facility.getLat()),
                toCoordinate(facility.getLng()),
                distanceM,
                facility.getPetAllowed(),
                facility.getPetConditionRaw(),
                facility.getConfirmedAt(),
                facility.getImageUrl(),
                facility.getThumbnailUrl(),
                toPawGrade(aggregate),
                toRatings(aggregate),
                toOwnedPets(myPets),
                hasNonDogCatPet(myPets)
        );
    }

    /** 등급 판정은 표시용으로 반올림하기 전 원점수로 한다. 87.96이 88.0이 되며 한 등급 올라가면 안 된다. */
    private static FacilityResponseDTO.PawGrade toPawGrade(FacilityReviewAggregate aggregate) {
        long reviewCount = aggregate == null ? 0 : aggregate.reviewCount();
        double score = aggregate == null ? 0 : aggregate.averageScore();
        PetFriendlyGrade grade = PetFriendlyGrade.ofScore(score, reviewCount);

        return new FacilityResponseDTO.PawGrade(
                PetFriendlyGrade.levelOf(grade),
                PetFriendlyGrade.displayLabelOf(grade, reviewCount)
        );
    }

    /** 리뷰가 없으면 0점이 아니라 "아직 없음"이므로 객체 자체를 내리지 않는다. */
    private static FacilityResponseDTO.Ratings toRatings(FacilityReviewAggregate aggregate) {
        if (aggregate == null) {
            return null;
        }

        return new FacilityResponseDTO.Ratings(
                Numbers.roundToOneDecimal(aggregate.averageScore()),
                Numbers.roundToOneDecimal(aggregate.averageSpace()),
                Numbers.roundToOneDecimal(aggregate.averageStaff()),
                Numbers.roundToOneDecimal(aggregate.averageAmenity())
        );
    }

    private static List<FacilityResponseDTO.OwnedPet> toOwnedPets(List<Pet> myPets) {
        return myPets.stream()
                .map(pet -> new FacilityResponseDTO.OwnedPet(
                        pet.getPetId(),
                        pet.getName(),
                        pet.getWeight()
                ))
                .toList();
    }

    /** 개·고양이 외 반려동물은 동반 조건이 시설마다 크게 달라 화면이 별도 유의사항을 띄운다. */
    private static boolean hasNonDogCatPet(List<Pet> myPets) {
        return myPets.stream()
                .anyMatch(pet -> pet.getKind() != Kind.DOG && pet.getKind() != Kind.CAT);
    }

    /**
     * 좌표는 {@code numeric(10,7)}이라 {@code BigDecimal} 그대로 내보내면 소수점 일곱 자리가
     * 살아 {@code 37.8016000}으로 직렬화된다. 지도 좌표에 double의 유효숫자는 차고 넘친다.
     */
    private static Double toCoordinate(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
