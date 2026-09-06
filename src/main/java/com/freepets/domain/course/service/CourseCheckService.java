package com.freepets.domain.course.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.course.dto.CourseCheckResponseDTO;
import com.freepets.domain.course.entity.CourseDistanceOption;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.facility.service.BoundingBox;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petcheck.converter.PetCheckConverter;
import com.freepets.domain.petcheck.entity.PetCheck;
import com.freepets.domain.petcheck.entity.PetCheckResult;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.petcheck.service.PetCheckJudgeService;
import com.freepets.domain.petcheck.service.PetCheckJudgeService.GroupVerdict;
import com.freepets.domain.petcheck.service.PetCheckJudgeService.PetVerdict;
import com.freepets.domain.petcheck.service.VerifyCodeGenerator;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.global.util.GeoUtils;

import lombok.RequiredArgsConstructor;

/**
 * POST /api/v1/ai/course-check — 사용자가 직접 담은 코스(시설 여러 개)를 06번 판별과 동일한
 * 규칙({@link PetCheckJudgeService#judgeGroup})으로 일괄 검증한다. 스톱마다 판별 결과를
 * {@code pet_checks} 이력에 남긴다(08-course-check.md "확인 필요" 1번, 이력으로 남기는 쪽으로 결정).
 *
 * <p>{@code CONDITIONAL}은 대안을 제안하지 않고 통과 처리한다 — 반려동물이 못 들어가는 게 아니라
 * 조건부로 들어갈 수 있는 것이므로. {@code DENIED}인 스톱만 대안을 찾는다.
 *
 * <p>대안은 미리 저장해둔 테이블을 찾는 게 아니라 그 자리에서 계산한다(08-course-check.md
 * "같은 카테고리·그룹 통과·거리순") — 같은 {@code category} · 이 코스에 이미 없음 · DENIED 스톱
 * 반경 {@link #ALTERNATIVE_SEARCH_RADIUS_METERS} 이내 · 선택한 반려동물 전체가 {@code
 * judgeGroup}을 통과(DENIED 아님)하는 시설 중 가장 가까운 1곳. 조건에
 * 맞는 곳이 하나도 없으면 {@code null} — 프론트가 "같은 성격의 대체 시설을 찾지 못했어요. 이
 * 스톱은 빼는 것을 권장해요"로 안내한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CourseCheckService {

    /** 첫 스톱 시각. 08-course-check.md 데모 고정값. */
    private static final LocalTime FIRST_STOP_TIME = LocalTime.of(10, 0);

    /** 스톱 간 간격(분). 08-course-check.md 데모 고정값. */
    private static final int MINUTES_PER_STOP = 90;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 대안 후보 판별(judgeGroup, 시설 하나당 반려동물 수만큼 규칙을 도는 무거운 호출)은 거리순
     * 상위 이만큼만 한다. DB 조회 자체는 {@link #ALTERNATIVE_SEARCH_RADIUS} 경계 사각형으로 이미
     * 좁혀오지만(FacilityRepository.findAlternativeCandidates), 그 경계 안에서도 후보가 많을 수
     * 있어 판별 호출 수를 한 번 더 상수로 고정한다(CourseSimilarService의 CANDIDATE_JUDGE_LIMIT과
     * 같은 이유) — 거리는 DB 조회 없이 계산 가능하니 먼저 정렬해서 좁힌 뒤 판별한다.
     */
    private static final int ALTERNATIVE_CANDIDATE_LIMIT = 20;

    /**
     * 대안을 찾을 반경 — DENIED 스톱과 같은 카테고리 시설 전부(지역 조건 없이, 예: RESTAURANT
     * 전국 1만여 곳)를 불러온 뒤에야 거리로 거르던 예전 방식은 실제로 안 쓰일 먼 후보까지 매번
     * DB에서 불러와 낭비가 컸다 — 코스 자체가 "같은 날 돌아볼 동선"이라는 전제(liked/similar/preset
     * 전부 최대 {@link CourseDistanceOption#THIRTY_KM}까지만 스톱 간 거리를 허용) 위에서, 그보다
     * 먼 대안은 애초에 이 코스에 실용적이지 않다고 보고 같은 값을 반경으로 쓴다.
     */
    private static final int ALTERNATIVE_SEARCH_RADIUS_METERS = CourseDistanceOption.THIRTY_KM.getMeters();

    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final FacilityRepository facilityRepository;
    private final PetCheckRepository petCheckRepository;
    private final PetCheckJudgeService petCheckJudgeService;

    public CourseCheckResponseDTO.CourseCheckResult checkCourse(
            Long userId,
            List<Long> petIds,
            List<Long> facilityIds
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        List<Pet> pets = findOwnedPets(userId, petIds);
        List<Facility> facilitiesInOrder = findFacilitiesInOrder(facilityIds);
        Set<Long> courseFacilityIds = Set.copyOf(facilityIds);

        List<CourseCheckResponseDTO.Stop> stops = new ArrayList<>();
        PetCheckResult courseOverall = PetCheckResult.ALLOWED;
        long blockedCount = 0;

        for (int i = 0; i < facilitiesInOrder.size(); i++) {
            Facility facility = facilitiesInOrder.get(i);
            GroupVerdict verdict = petCheckJudgeService.judgeGroup(pets, facility);

            saveAsHistory(user, facility, verdict);

            boolean isDenied = verdict.overall() == PetCheckResult.DENIED;
            stops.add(new CourseCheckResponseDTO.Stop(
                    toFacilitySummary(facility),
                    stopTimeOf(i),
                    toStopVerdicts(verdict),
                    verdict.overall(),
                    isDenied ? findAlternative(facility, courseFacilityIds, pets) : null
            ));

            if (isDenied) {
                blockedCount++;
            }
            courseOverall = PetCheckResult.mostSevere(courseOverall, verdict.overall());
        }

        return new CourseCheckResponseDTO.CourseCheckResult(courseOverall, blockedCount, stops);
    }

    private CourseCheckResponseDTO.Alternative findAlternative(
            Facility blockedFacility,
            Set<Long> courseFacilityIds,
            List<Pet> pets
    ) {
        // blockedFacility 자체가 좌표가 없으면(관광공사 원본에 좌표가 없거나 한반도 밖 좌표라 동기화
        // 시점에 null로 걸러진 시설 — FacilityRepository.SEARCH_FILTER 주석 참고) distanceFrom을
        // 계산할 기준점이 없다. candidates는 좌표 유무를 걸러내면서 정작 origin은 안 걸러서, 이런
        // 시설이 코스에 있으면 GeoUtils.distanceMeters(null, ...)에서 NPE가 났다 — 대안을 못 찾은
        // 것과 같게 취급해 null을 반환한다.
        if (blockedFacility.getLat() == null || blockedFacility.getLng() == null) {
            return null;
        }

        // 경계 사각형으로 먼저 좁혀서 불러온다 — idx_facilities_coordinate를 타는 단순 범위
        // 비교라, 지역 조건 없이 카테고리 전체를 불러오는 것보다 훨씬 적은 행만 애플리케이션으로
        // 넘어온다(BoundingBox 클래스 주석 참고). 사각형은 원의 외접이라 모서리 쪽 후보는 실제로
        // ALTERNATIVE_SEARCH_RADIUS_METERS보다 살짝 멀 수 있지만, 어차피 거리순 정렬 후 상위
        // ALTERNATIVE_CANDIDATE_LIMIT개만 쓰므로 정확한 반경 자르기는 필요 없다.
        BoundingBox boundingBox = BoundingBox.around(
                blockedFacility.getLat().doubleValue(), blockedFacility.getLng().doubleValue(), ALTERNATIVE_SEARCH_RADIUS_METERS
        );
        List<Facility> candidates = facilityRepository.findAlternativeCandidates(
                blockedFacility.getCategory(), PetAllowed.DENIED, courseFacilityIds,
                boundingBox.minimumLatitude(), boundingBox.maximumLatitude(),
                boundingBox.minimumLongitude(), boundingBox.maximumLongitude()
        );

        // 거리부터 정렬해 가까운 ALTERNATIVE_CANDIDATE_LIMIT곳으로 좁힌 뒤에만 판별한다 — 이미
        // 거리순이라 그중 첫 통과 후보가 곧 "판별을 통과한 가장 가까운 곳"이다. 거리가 정확히
        // 같은 후보끼리는 facilityId로 순서를 고정한다 — findAlternativeCandidates에 ORDER BY가
        // 없어(어차피 여기서 다시 정렬하니 DB 정렬은 낭비), 동순위 후보의 원래 반환 순서가 호출마다
        // 달라질 수 있다(FacilityRepository.ORDER_BY_DISTANCE와 같은 이유).
        return candidates.stream()
                .sorted(Comparator.comparingDouble((Facility candidate) -> distanceFrom(blockedFacility, candidate))
                        .thenComparing(Facility::getFacilityId))
                .limit(ALTERNATIVE_CANDIDATE_LIMIT)
                .filter(candidate -> petCheckJudgeService.judgeGroup(pets, candidate).overall() != PetCheckResult.DENIED)
                .findFirst()
                .map(nearest -> toAlternative(nearest, distanceFrom(blockedFacility, nearest)))
                .orElse(null);
    }

    private double distanceFrom(
            Facility origin,
            Facility target
    ) {
        return GeoUtils.distanceMeters(origin.getLat(), origin.getLng(), target.getLat(), target.getLng());
    }

    private CourseCheckResponseDTO.Alternative toAlternative(
            Facility alternative,
            double distanceMeters
    ) {
        return new CourseCheckResponseDTO.Alternative(
                alternative.getFacilityId(),
                alternative.getName(),
                Math.round(distanceMeters / 100.0) / 10.0 // km, 소수 첫째 자리
        );
    }

    private void saveAsHistory(
            User user,
            Facility facility,
            GroupVerdict verdict
    ) {
        PetCheck petCheck = PetCheck.builder()
                .user(user)
                .facility(facility)
                .overall(verdict.overall())
                .build();

        for (PetVerdict petVerdict : verdict.verdicts()) {
            // 코스 일괄 판별도 같은 pet_check_verdicts 테이블에 이력을 남기므로, POST /ai/check와
            // 동일하게 검증 코드를 발급해둔다 — VerifyCodeGenerator.generate() 참고.
            petCheck.addVerdict(PetCheckConverter.toVerdictEntity(petVerdict, VerifyCodeGenerator.generate()));
        }

        petCheckRepository.save(petCheck);
    }

    private List<CourseCheckResponseDTO.StopVerdict> toStopVerdicts(GroupVerdict verdict) {
        return verdict.verdicts().stream()
                .map(petVerdict -> new CourseCheckResponseDTO.StopVerdict(
                        petVerdict.pet().getPetId(),
                        petVerdict.pet().getName(),
                        petVerdict.result(),
                        petVerdict.reason(),
                        petVerdict.conditions()
                ))
                .toList();
    }

    private CourseCheckResponseDTO.FacilitySummary toFacilitySummary(Facility facility) {
        return new CourseCheckResponseDTO.FacilitySummary(
                facility.getFacilityId(),
                facility.getName(),
                facility.getCategory()
        );
    }

    private String stopTimeOf(int stopIndex) {
        return FIRST_STOP_TIME.plusMinutes((long) MINUTES_PER_STOP * stopIndex).format(TIME_FORMAT);
    }

    private List<Facility> findFacilitiesInOrder(List<Long> facilityIds) {
        Map<Long, Facility> byId = new LinkedHashMap<>();
        facilityRepository.findAllById(facilityIds).forEach(facility -> byId.put(facility.getFacilityId(), facility));

        List<Facility> ordered = new ArrayList<>();
        for (Long facilityId : facilityIds) {
            Facility facility = byId.get(facilityId);
            if (facility == null) {
                throw new GeneralException(ErrorStatus.FACILITY4001);
            }
            ordered.add(facility);
        }
        return ordered;
    }

    // PetCheckCommandService.findOwnedPets와 같은 이유 — petIds 중복 제거 후 소유 검증.
    private List<Pet> findOwnedPets(
            Long userId,
            List<Long> petIds
    ) {
        List<Long> distinctPetIds = petIds.stream().distinct().toList();
        List<Pet> pets = petRepository.findAllByPetIdInAndDeletedAtIsNull(distinctPetIds);

        if (pets.size() != distinctPetIds.size()) {
            throw new GeneralException(ErrorStatus.PET4001);
        }

        boolean isAllOwnedByUser = pets.stream().allMatch(pet -> pet.isOwnedBy(userId));
        if (!isAllOwnedByUser) {
            throw new GeneralException(ErrorStatus.PET4002);
        }

        return pets;
    }

}
