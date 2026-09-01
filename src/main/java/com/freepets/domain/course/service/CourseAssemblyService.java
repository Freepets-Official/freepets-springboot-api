package com.freepets.domain.course.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.global.util.GeoUtils;

/**
 * "정렬된 후보 → 최종 스톱"의 마지막 조립 단계. {@code liked}·{@code similar} 둘 다 이 메서드를
 * 공유한다(07-courses.md 공통 루틴) — 두 모듈이 서로 다른 결과를 내면 안 되는 공통 규칙이라 하나로
 * 둔다.
 */
@Service
public class CourseAssemblyService {

    /** 추천 코스 스톱 상한. liked/similar 공통(07-courses.md "결정된 사항"). */
    public static final int MAX_RECOMMENDED_STOPS = 4;

    /**
     * @param candidatesScoreDescSorted 점수(평균 만족도 or 유사도) desc로 이미 정렬된 후보. 좌표
     *                                  없는 시설은 최근접-이웃 재정렬을 할 수 없어 조립 전에 제외한다.
     */
    public List<Facility> assemble(List<Facility> candidatesScoreDescSorted) {
        List<Facility> selected = pickTopPerCategory(withCoordinatesOnly(candidatesScoreDescSorted));
        return reorderByNearestNeighbor(selected);
    }

    /**
     * {@code preset}용 — 카테고리 다양성 규칙을 적용하지 않는다. "강릉 애견 카페 반나절 코스"처럼
     * 같은 카테고리(카페)로만 채워지는 테마 코스가 있어서, liked/similar와 달리 카테고리당 1곳
     * 제한을 걸면 안 된다. 점수 desc 순서로 상위 {@code limit}개를 그대로 뽑고 동선만 재정렬한다.
     */
    public List<Facility> assembleWithoutCategoryDiversity(
            List<Facility> candidatesScoreDescSorted,
            int limit
    ) {
        List<Facility> selected = withCoordinatesOnly(candidatesScoreDescSorted).stream()
                .limit(limit)
                .toList();
        return reorderByNearestNeighbor(selected);
    }

    private List<Facility> withCoordinatesOnly(List<Facility> candidates) {
        return candidates.stream()
                .filter(facility -> facility.getLat() != null && facility.getLng() != null)
                .toList();
    }

    /** 앞에서부터(=점수 높은 순) 순회하며 카테고리별로 처음 만나는(=그 카테고리 최고점) 1곳만 채택. */
    private List<Facility> pickTopPerCategory(List<Facility> candidatesScoreDescSorted) {
        List<Facility> selected = new ArrayList<>();
        Set<FacilityCategory> usedCategories = new HashSet<>();

        for (Facility candidate : candidatesScoreDescSorted) {
            if (selected.size() >= MAX_RECOMMENDED_STOPS) {
                break;
            }
            if (usedCategories.add(candidate.getCategory())) {
                selected.add(candidate);
            }
        }

        return selected;
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
