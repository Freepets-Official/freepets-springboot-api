package com.freepets.domain.course.service;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.global.util.GeoUtils;

/**
 * "정렬된 후보 → 최종 스톱"의 마지막 조립 단계. {@code liked}·{@code similar}·{@code preset} 셋 다
 * 이 서비스를 공유한다(07-courses.md 공통 루틴) — 다른 모듈이 서로 다른 결과를 내면 안 되는 공통
 * 규칙이라 하나로 둔다.
 *
 * <p>두 가지를 강제한다 — 점수만 보고 뽑으면 "동선이 실제로는 말이 안 되는" 코스가 나올 수 있어서다
 * (예: 스톱끼리 65km씩 떨어짐, 같은 카테고리로만 채워짐):
 * <ul>
 *   <li>스톱 간 거리 — 이미 채택된 스톱 중 하나와는 호출부가 넘긴 {@code maxDistanceMeters} 이내여야
 *       한다. 도보 기준({@link #DEFAULT_MAX_STOP_DISTANCE_METERS})이 기본값이지만, 차량 이용처럼
 *       더 넉넉해도 되는 경우를 위해 값을 강제하지 않고 호출부(사용자 입력)가 정하게 한다.</li>
 *   <li>카테고리 쏠림 방지 — 한 카테고리가 전체 스톱의 절반을 넘지 못한다. "애견 카페 코스"처럼
 *       후보가 원래 한 카테고리뿐인 테마는 이 규칙이 자연히 무력화되어(카테고리가 하나면 상한도
 *       스톱 수와 같아짐) 기존 동작 그대로다.</li>
 * </ul>
 *
 * <p>{@code mealCandidates}를 받는 오버로드({@link #assemble(List, List, double)},
 * {@link #assembleWithoutCategoryDiversity(List, List, int, double)}, {@link #appendMealStop})는
 * 마지막 한 자리를 근처 식사 스톱(대개 RESTAURANT 카테고리)으로 채운다 — 코스가 테마 하나로만
 * 채워지는 걸(예: 힐링 코스가 온통 사찰·온천뿐) 완화하기 위한 선택 사항이다. 후보가 비어 있으면
 * (지역 필터가 없어 호출부가 못 구한 경우 등) 식사 스톱 없이 기존 동작과 동일하게 동작한다.
 */
@Service
public class CourseAssemblyService {

    /** 추천 코스 스톱 상한. liked/similar/preset 공통(07-courses.md "결정된 사항"). */
    public static final int MAX_RECOMMENDED_STOPS = 4;

    /**
     * 사용자가 직접 담는 코스(CUSTOM 저장, 순서 최적화, 일괄 판별)의 스톱 개수 상한 —
     * {@link #MAX_RECOMMENDED_STOPS}(AI 추천 4곳)보다 여유를 준다. 상한이 없으면 요청 하나에
     * 스톱을 아주 많이 담아 매 스톱마다 도는 무거운 판별(판별 API들이 스톱 수만큼 {@code
     * PetCheckJudgeService.judgeGroup}을 호출)이 그만큼 늘어나는 걸 막는다(제품 결정).
     */
    public static final int MAX_CUSTOM_STOPS = 10;

    /** 사용자가 {@code maxDistanceM}을 안 보냈을 때 쓰는 기본값(m) — 도보 기준. */
    public static final double DEFAULT_MAX_STOP_DISTANCE_METERS = 5000;

    /**
     * @param candidatesScoreDescSorted 점수(평균 만족도 or 유사도) desc로 이미 정렬된 후보. 좌표
     *                                  없는 시설은 최근접-이웃 재정렬을 할 수 없어 조립 전에 제외한다.
     * @param maxDistanceMeters         스톱 간 허용 최대 거리(m). 차량 이용 등으로 더 넉넉하게
     *                                  잡고 싶으면 호출부가 {@link #DEFAULT_MAX_STOP_DISTANCE_METERS}
     *                                  대신 더 큰 값을 넘기면 된다.
     */
    public List<Facility> assemble(
            List<Facility> candidatesScoreDescSorted,
            double maxDistanceMeters
    ) {
        return select(candidatesScoreDescSorted, MAX_RECOMMENDED_STOPS, 1, maxDistanceMeters);
    }

    /**
     * {@link #assemble(List, double)}에 "식사 스톱" 한 자리를 더한 버전 — 테마·취향 후보만으로
     * 채우면 코스가 전부 같은 성격(예: 힐링 코스가 온통 사찰·온천뿐)이 되기 쉬워서, 마지막
     * 한 자리는 근처 식당(RESTAURANT) 후보로 채운다.
     *
     * @param mealCandidates 식사 스톱 후보(대개 지역 한정 RESTAURANT 카테고리). 지역 필터가 없어
     *                       호출부가 후보를 못 구했으면 빈 리스트를 넘기면 된다 — 그 경우
     *                       {@link #assemble(List, double)}와 동일하게 동작한다.
     */
    public List<Facility> assemble(
            List<Facility> candidatesScoreDescSorted,
            List<Facility> mealCandidates,
            double maxDistanceMeters
    ) {
        return selectWithMealStop(candidatesScoreDescSorted, mealCandidates, MAX_RECOMMENDED_STOPS, 1, maxDistanceMeters);
    }

    /**
     * {@code preset}용 — 카테고리당 정확히 1곳으로 제한하지는 않는다. "강릉 애견 카페 반나절
     * 코스"처럼 후보가 원래 한 카테고리뿐인 테마 코스가 있어서, {@link #assemble}처럼 "카테고리당
     * 1곳"을 걸면 스톱이 1개로 줄어버린다. 대신 "한 카테고리가 전체의 절반을 못 넘음" 정도로만
     * 다양성을 강제한다 — 후보 카테고리가 하나뿐이면 이 상한도 {@code limit}과 같아져 사실상
     * 무제한이 된다.
     */
    public List<Facility> assembleWithoutCategoryDiversity(
            List<Facility> candidatesScoreDescSorted,
            int limit,
            double maxDistanceMeters
    ) {
        return select(candidatesScoreDescSorted, limit, maxPerCategory(candidatesScoreDescSorted, limit), maxDistanceMeters);
    }

    /** {@link #assembleWithoutCategoryDiversity(List, int, double)}의 식사 스톱 포함 버전 — {@link #assemble(List, List, double)}와 같은 이유. */
    public List<Facility> assembleWithoutCategoryDiversity(
            List<Facility> candidatesScoreDescSorted,
            List<Facility> mealCandidates,
            int limit,
            double maxDistanceMeters
    ) {
        return selectWithMealStop(
                candidatesScoreDescSorted, mealCandidates, limit, maxPerCategory(candidatesScoreDescSorted, limit), maxDistanceMeters
        );
    }

    private int maxPerCategory(
            List<Facility> candidatesScoreDescSorted,
            int limit
    ) {
        long distinctCategoryCount = candidatesScoreDescSorted.stream()
                .map(Facility::getCategory)
                .distinct()
                .count();
        return distinctCategoryCount <= 1
                ? limit
                : (int) Math.ceil(limit / 2.0);
    }

    /**
     * 점수 desc 순서로 훑으며, 카테고리 상한과 거리 제약을 둘 다 만족하는 시설만 채택한다. 거리
     * 제약은 이미 채택된 스톱 중 "가장 가까운 것"과 비교한다 — 앵커(점수 1위) 하나만 기준으로
     * 삼으면 코스가 한 점 주변에서만 못 벗어나므로, 자연스러운 동선(체인)을 허용하기 위함이다.
     */
    private List<Facility> select(
            List<Facility> candidatesScoreDescSorted,
            int limit,
            int maxPerCategory,
            double maxDistanceMeters
    ) {
        List<Facility> selected = new ArrayList<>();
        Map<FacilityCategory, Integer> countByCategory = new HashMap<>();

        for (Facility candidate : withCoordinatesOnly(candidatesScoreDescSorted)) {
            if (selected.size() >= limit) {
                break;
            }
            if (countByCategory.getOrDefault(candidate.getCategory(), 0) >= maxPerCategory) {
                continue;
            }
            if (!selected.isEmpty() && isTooFarFromAll(candidate, selected, maxDistanceMeters)) {
                continue;
            }

            selected.add(candidate);
            countByCategory.merge(candidate.getCategory(), 1, Integer::sum);
        }

        return reorderByNearestNeighbor(selected);
    }

    /**
     * {@code limit}개 중 마지막 한 자리를 식사 스톱으로 예약한다 — 나머지 {@code limit - 1}개를
     * 먼저 {@link #select}로 채운 뒤, 그 스톱들과 가장 가까운 식사 후보 하나를 {@link
     * #appendMealStop}로 덧붙인다. 식사 후보가 하나도 안 맞으면(전부 {@code maxDistanceMeters}
     * 밖) 자리를 비워두지 않고 원래대로 {@code limit}개 전부를 테마 후보로 채운다.
     */
    private List<Facility> selectWithMealStop(
            List<Facility> candidatesScoreDescSorted,
            List<Facility> mealCandidates,
            int limit,
            int maxPerCategory,
            double maxDistanceMeters
    ) {
        if (mealCandidates.isEmpty() || limit <= 1) {
            return select(candidatesScoreDescSorted, limit, maxPerCategory, maxDistanceMeters);
        }

        List<Facility> reserved = select(candidatesScoreDescSorted, limit - 1, maxPerCategory, maxDistanceMeters);
        List<Facility> withMealStop = appendMealStop(reserved, mealCandidates, maxDistanceMeters);
        if (withMealStop.size() > reserved.size()) {
            return withMealStop;
        }

        return select(candidatesScoreDescSorted, limit, maxPerCategory, maxDistanceMeters);
    }

    /**
     * 이미 조립된 스톱 목록에 근처 식사 후보(대개 RESTAURANT 카테고리) 하나를 덧붙인다. 이미
     * 채택된 스톱 중 어느 하나와도 {@code maxDistanceMeters} 이내인 후보가 없으면(혹은 애초에
     * {@code stops}가 비었으면) 원래 목록을 그대로 반환한다 — 억지로 먼 식당을 끼워넣지 않는다.
     * preset은 캐시된 풀에서 표시분을 뽑은 뒤 이 메소드로 직접 식사 스톱을 덧붙이고, liked·similar는
     * {@link #assemble(List, List, double)}를 통해 간접적으로 쓴다.
     */
    public List<Facility> appendMealStop(
            List<Facility> stops,
            List<Facility> mealCandidates,
            double maxDistanceMeters
    ) {
        if (stops.isEmpty() || mealCandidates.isEmpty()) {
            return stops;
        }

        Facility mealStop = withCoordinatesOnly(mealCandidates).stream()
                .filter(candidate -> stops.stream().noneMatch(stop -> stop.getFacilityId().equals(candidate.getFacilityId())))
                .map(candidate -> new AbstractMap.SimpleEntry<>(candidate, nearestStopDistanceMeters(candidate, stops)))
                .filter(entry -> entry.getValue() <= maxDistanceMeters)
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        if (mealStop == null) {
            return stops;
        }

        List<Facility> withMealStop = new ArrayList<>(stops);
        withMealStop.add(mealStop);
        return reorderByNearestNeighbor(withMealStop);
    }

    private double nearestStopDistanceMeters(
            Facility candidate,
            List<Facility> stops
    ) {
        return stops.stream()
                .mapToDouble(stop -> GeoUtils.distanceMeters(
                        stop.getLat(), stop.getLng(), candidate.getLat(), candidate.getLng()
                ))
                .min()
                .orElse(Double.MAX_VALUE);
    }

    /**
     * CUSTOM 코스를 직접 편집(검색해서 추가·삭제·순서 변경)한 뒤 동선만 다듬고 싶을 때 쓴다.
     * {@link #select}와 달리 카테고리 상한·거리 제약으로 스톱을 걸러내지 않는다 — 사용자가 이미
     * 고른 스톱은 전부 유지하고 순서만 최근접 이웃 방식으로 재배치한다.
     *
     * <p>좌표 없는 시설은 재배치 대상에서 빠진다(거리 계산 불가) — 다만 스톱 자체를 잃으면 안 되니
     * 원래 상대 순서를 유지한 채 재배치된 목록 뒤에 그대로 붙인다.
     */
    public List<Facility> reorderForCustomEdit(List<Facility> stopsInOrder) {
        Map<Boolean, List<Facility>> partitioned = stopsInOrder.stream()
                .collect(Collectors.partitioningBy(facility -> facility.getLat() != null && facility.getLng() != null));

        List<Facility> reordered = new ArrayList<>(reorderByNearestNeighbor(partitioned.get(true)));
        reordered.addAll(partitioned.get(false));
        return reordered;
    }

    private boolean isTooFarFromAll(
            Facility candidate,
            List<Facility> selected,
            double maxDistanceMeters
    ) {
        return selected.stream()
                .mapToDouble(stop -> GeoUtils.distanceMeters(
                        stop.getLat(), stop.getLng(), candidate.getLat(), candidate.getLng()
                ))
                .min()
                .orElse(Double.MAX_VALUE) > maxDistanceMeters;
    }

    private List<Facility> withCoordinatesOnly(List<Facility> candidates) {
        return candidates.stream()
                .filter(facility -> facility.getLat() != null && facility.getLng() != null)
                .toList();
    }

    /**
     * 점수 1위 시설을 시작점으로, 매번 아직 안 고른 스톱 중 직전 스톱과 가장 가까운 곳을 다음으로
     * 선택한다 — 동선이 왔다갔다 하지 않게.
     */
    private List<Facility> reorderByNearestNeighbor(List<Facility> selected) {
        if (selected.size() <= 1) {
            return selected;
        }

        List<Facility> remaining = new ArrayList<>(selected);
        List<Facility> ordered = new ArrayList<>();

        Facility current = remaining.remove(0);
        ordered.add(current);

        while (!remaining.isEmpty()) {
            Facility current2 = current;
            Facility nearest = remaining.stream()
                    .min(Comparator.comparingDouble(facility -> GeoUtils.distanceMeters(
                            current2.getLat(), current2.getLng(), facility.getLat(), facility.getLng()
                    )))
                    .orElseThrow();

            remaining.remove(nearest);
            ordered.add(nearest);
            current = nearest;
        }

        return ordered;
    }

}
