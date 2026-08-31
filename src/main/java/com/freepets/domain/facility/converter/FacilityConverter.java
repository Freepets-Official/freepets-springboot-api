package com.freepets.domain.facility.converter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.freepets.domain.facility.dto.FacilityResponseDTO;
import com.freepets.domain.facility.entity.CheckList;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.PetFriendlyGrade;
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
                facility.getPetScore(),
                PetFriendlyGrade.labelOf(facility.getPetScore(), reviewCount),
                reviewCount
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
