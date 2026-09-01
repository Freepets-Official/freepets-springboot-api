package com.freepets.domain.course.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *   <li>스톱 간 거리 — 이미 채택된 스톱 중 하나와는 {@link #MAX_STOP_DISTANCE_METERS} 이내여야
 *       한다. 도보 기준 값이라, 차량 이용을 전제한 조정(더 넉넉한 반경)은 추후 과제.</li>
 *   <li>카테고리 쏠림 방지 — 한 카테고리가 전체 스톱의 절반을 넘지 못한다. "애견 카페 코스"처럼
 *       후보가 원래 한 카테고리뿐인 테마는 이 규칙이 자연히 무력화되어(카테고리가 하나면 상한도
 *       스톱 수와 같아짐) 기존 동작 그대로다.</li>
 * </ul>
 */
@Service
public class CourseAssemblyService {

    /** 추천 코스 스톱 상한. liked/similar/preset 공통(07-courses.md "결정된 사항"). */
    public static final int MAX_RECOMMENDED_STOPS = 4;

    /**
     * 스톱 간 최대 거리(m). 사용자 제안값(도보 기준 5km) — 차량 이용 시 더 멀어도 괜찮다는
     * 의견도 있었으나, "이동수단" 입력 자체가 지금 API에 없어 일단 도보 기준 단일값으로 둔다.
     */
    private static final double MAX_STOP_DISTANCE_METERS = 5000;

    /**
     * @param candidatesScoreDescSorted 점수(평균 만족도 or 유사도) desc로 이미 정렬된 후보. 좌표
     *                                  없는 시설은 최근접-이웃 재정렬을 할 수 없어 조립 전에 제외한다.
     */
    public List<Facility> assemble(List<Facility> candidatesScoreDescSorted) {
        return select(candidatesScoreDescSorted, MAX_RECOMMENDED_STOPS, 1);
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
            int limit
    ) {
        long distinctCategoryCount = candidatesScoreDescSorted.stream()
                .map(Facility::getCategory)
                .distinct()
                .count();
        int maxPerCategory = distinctCategoryCount <= 1
                ? limit
                : (int) Math.ceil(limit / 2.0);

        return select(candidatesScoreDescSorted, limit, maxPerCategory);
    }

    /**
     * 점수 desc 순서로 훑으며, 카테고리 상한과 거리 제약을 둘 다 만족하는 시설만 채택한다. 거리
     * 제약은 이미 채택된 스톱 중 "가장 가까운 것"과 비교한다 — 앵커(점수 1위) 하나만 기준으로
     * 삼으면 코스가 한 점 주변에서만 못 벗어나므로, 자연스러운 동선(체인)을 허용하기 위함이다.
     */
    private List<Facility> select(
            List<Facility> candidatesScoreDescSorted,
            int limit,
            int maxPerCategory
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
            if (!selected.isEmpty() && isTooFarFromAll(candidate, selected)) {
                continue;
            }

            selected.add(candidate);
            countByCategory.merge(candidate.getCategory(), 1, Integer::sum);
        }

        return reorderByNearestNeighbor(selected);
    }

    private boolean isTooFarFromAll(
            Facility candidate,
            List<Facility> selected
    ) {
        return selected.stream()
                .mapToDouble(stop -> GeoUtils.distanceMeters(
                        stop.getLat(), stop.getLng(), candidate.getLat(), candidate.getLng()
                ))
                .min()
                .orElse(Double.MAX_VALUE) > MAX_STOP_DISTANCE_METERS;
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
