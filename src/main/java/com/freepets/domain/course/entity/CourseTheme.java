package com.freepets.domain.course.entity;

import java.util.Set;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;

/**
 * 프리셋 코스(source=PRESET)의 테마. 지역(sido/sigungu) × 이 값 조합으로 배치 계산·캐시한다.
 *
 * <p>1차 제안 5종 — 실제 화면 기획(예: "강릉 바다 산책 1일 코스", "강릉 애견 카페 반나절 코스")에서
 * 확인된 두 개(SEASIDE_WALK, PET_CAFE)를 기준으로 나머지 세 개를 채웠다. 추후 화면 기획이 늘어나면
 * 값을 더 추가하면 된다 — 기존 캐시된 코스에는 영향 없음(값 추가는 하위 호환).
 *
 * <p>{@code categories}(자사 8종 대분류)만으로는 테마를 정확히 못 고른다 — 예를 들어 TOUR
 * 하나가 SEASIDE_WALK·HEALING·SIGHTSEEING 세 테마에 전부 걸쳐 있어서, "바다 산책"으로 찾아도
 * 산속 사찰 같은 안 어울리는 TOUR 시설이 섞여 나올 수 있었다. {@code smallCategoryCodes}는
 * 관광공사가 이미 매겨둔 훨씬 촘촘한 분류체계(lclsSystm3, {@link Facility#getSmallCategoryCode()})
 * 값으로, 실제로 이 테마에 어울리는 소분류만 정확히 골라낸다 — 새로 배치를 돌리거나 데이터를
 * 채울 필요 없이 이미 동기화 때 저장된 값을 그대로 쓴다. 최종 판정({@link #matchesFacilityDetail})은
 * 소분류만으로 하고, {@code categories}는 {@link
 * com.freepets.domain.course.service.CoursePresetService}가 DB 쿼리에서 후보를 1차로 좁히는
 * 용도로만 쓴다({@link com.freepets.domain.facility.repository.FacilityRepository#findPresetCandidates}) —
 * 자세한 이유는 {@link #matchesFacilityDetail} 참고.
 *
 * <p>{@code categories}는 후보 시설을 고르는 필터다. PET_CAFE처럼 카테고리가 하나뿐인 테마는
 * liked/similar의 "카테고리당 1곳" 규칙을 적용하면 스톱이 1개로 줄어버리므로, preset은
 * {@link com.freepets.domain.course.service.CourseAssemblyService#assembleWithoutCategoryDiversity}
 * (다양성 규칙 미적용)로 조립한다.
 */
public enum CourseTheme {

    /** 해변·산책로 위주. */
    SEASIDE_WALK(
            "바다 산책",
            Set.of(FacilityCategory.TOUR, FacilityCategory.LEISURE),
            Set.of(
                    "NA020500", // 섬
                    "NA020700", // 항구/포구
                    "NA020800", // 해안절경
                    "NA020900"  // 해변. 해수욕장
            )
    ),

    /** 반려동물 동반 카페 위주. */
    PET_CAFE(
            "애견 카페",
            Set.of(FacilityCategory.CAFE),
            Set.of(
                    "FD050100", // 카페
                    "FD050200", // 찻집
                    "FD050300"  // 기타음료점
            )
    ),

    /** 한적하고 여유로운 곳 위주. */
    HEALING(
            "힐링",
            Set.of(FacilityCategory.TOUR, FacilityCategory.CULTURE),
            Set.of(
                    "C01140001", // 힐링코스(관광공사가 직접 태깅한 추천코스)
                    "EX040100",  // 템플스테이
                    "EX040200",  // 사찰문화체험
                    "EX050100",  // 온천 / 사우나 / 스파
                    "EX050200",  // 찜질방
                    "EX050300",  // 한방체험
                    "EX050400",  // 힐링명상
                    "EX050500",  // 뷰티스파
                    "EX050600",  // 기타웰니스
                    "EX050700",  // 자연치유
                    "EX050800",  // 기타의료관광
                    "NA040600",  // 자연휴양림
                    "NA040700"   // 수목원ㆍ정원
            )
    ),

    /** 관광지(명소) 위주. categories에 LEISURE도 넣은 이유 — 실측 확인 시 이 소분류에 해당하는
     * 시설 중 극소수(국립공원 등)가 우리 카테고리 기준 LEISURE로도 잡혀 있었다. */
    SIGHTSEEING(
            "관광지 위주",
            Set.of(FacilityCategory.TOUR, FacilityCategory.CULTURE, FacilityCategory.FESTIVAL, FacilityCategory.LEISURE),
            Set.of(
                    "HS010100", "HS010200", "HS010300", "HS010400", "HS010500", "HS010600", // 고궁~민속마을
                    "HS010700", "HS010800", "HS010900", "HS011000", "HS011100", "HS011200", // 사적지~기타역사유적지
                    "HS020100", "HS020200", "HS020300", "HS020400", // 탑ㆍ비석ㆍ기념탑~기타역사유물
                    "HS030100", "HS030200", "HS030300", "HS030400", // 불교~기타종교성지
                    "HS040100", "HS040200", "HS040300", "HS040400", // 안보유적지~기타안보관광지
                    "NA010100", "NA010200", "NA010300", "NA010400", "NA010500", // 산~약수터
                    "NA040100", "NA040200", "NA040300", "NA040400", "NA040500", // 국립공원~생태관광지
                    "VE010100", "VE010200", "VE010300", "VE010400", "VE010500", // 건물~동상
                    "VE010600", "VE010700", "VE010800", "VE010900", // 터널~기타 건축/조형물
                    "VE040100", "VE040200", "VE040300", // 골목길·문화거리~둘레길
                    "VE070100", "VE070200", "VE070300", "VE070400", "VE070500", "VE070600" // 박물관~미술관/화랑
            )
    ),

    /**
     * 레포츠·액티비티 시설 위주. categories에 TOUR·CULTURE·STAY도 넣은 이유 — EX(체험관광)·VE02
     * (테마파크 등) 코드들이 실제로는 우리 카테고리 기준 대부분 TOUR(일부 CULTURE)로 잡혀 있어서,
     * LEISURE로만 좁히면 이 코드들에 매칭되는 시설 대다수가 DB 쿼리 단계에서부터 못 나온다
     * (실측 확인: EX01/02/03/06/07·VE02가 전부 TOUR, 일부만 CULTURE). STAY는 캠핑 코드(AC05)
     * 중 극소수가 우리 카테고리 기준 STAY로도 잡혀 있어서 추가했다.
     */
    ACTIVITY(
            "액티비티",
            Set.of(FacilityCategory.LEISURE, FacilityCategory.TOUR, FacilityCategory.CULTURE, FacilityCategory.STAY),
            Set.of(
                    "AC050100", "AC050200", "AC050300", "AC050400", // 일반야영장~글램핑장(캠핑)
                    "EX010100", // 전통문화체험
                    "EX020100", "EX020200", "EX020300", "EX020400", // 금속공예체험~기타공예체험
                    "EX030100", "EX030200", "EX030300", "EX030400", // 체험마을~체험어장
                    "EX060100", "EX060200", "EX060300", "EX060400", "EX060500", // 근대산업유산~산업테마거리
                    "EX060600", "EX060700", "EX060800", "EX060900", "EX061000", // 자동차/조선/철강~기타산업관광지
                    "EX070100", "EX070200", // 유람선/잠수함관광, 기타체험관광
                    "LS010100", "LS010200", "LS010300", "LS010400", "LS010500", // 인라인~경마
                    "LS010600", "LS010700", "LS010800", "LS010900", "LS011000", // 경륜~썰매장
                    "LS011100", "LS011200", "LS011300", "LS011400", "LS011500", // 수렵장~ATV
                    "LS011600", "LS011700", "LS011800", "LS011900", // MTB~기타육상레저스포츠
                    "LS020100", "LS020200", "LS020300", "LS020400", "LS020500", // 윈드서핑/제트스키~민물낚시
                    "LS020600", "LS020700", "LS020800", "LS020900", "LS021000", // 바다낚시~수상자전거
                    "LS021100", "LS021200", "LS021300", "LS021400", // 조정~기타수상레저스포츠
                    "LS030100", "LS030200", "LS030300", "LS030400", "LS030500", "LS030600", // 스카이다이빙~기타항공레저스포츠
                    "LS040100", // 복합레저스포츠
                    "VE020100", "VE020200", "VE020300", "VE020400", "VE020500", // 테마파크~천문대
                    "VE100100", "VE100200" // 스포츠경기장, 스포츠센터
            )
    );

    private final String label;
    private final Set<FacilityCategory> categories;
    private final Set<String> smallCategoryCodes;

    CourseTheme(
            String label,
            Set<FacilityCategory> categories,
            Set<String> smallCategoryCodes
    ) {
        this.label = label;
        this.categories = categories;
        this.smallCategoryCodes = smallCategoryCodes;
    }

    public String getLabel() {
        return label;
    }

    /** DB 쿼리에서 후보를 1차로 좁히는 대분류 필터 — {@link #matchesFacilityDetail}로 정밀 확인 전 단계. */
    public Set<FacilityCategory> getCategories() {
        return categories;
    }

    public Set<String> getSmallCategoryCodes() {
        return smallCategoryCodes;
    }

    /**
     * 이 시설이 실제로 이 테마에 어울리는지 — 소분류(smallCategoryCodes)만으로 판정한다.
     * 소분류 코드가 없는 시설(동기화 시점에 관광공사가 안 내려준 경우)은 이 테마에 속한다고
     * 확신할 근거가 없으므로 제외한다.
     *
     * <p>대분류(categories)는 여기서 확인하지 않는다 — 처음엔 categories도 같이 확인했었는데,
     * 실측해보니 관광공사 분류체계상 EX(체험관광)·VE02(테마파크 등) 코드가 우리 카테고리 기준
     * TOUR로 잡혀 있어서, ACTIVITY의 categories를 LEISURE로만 좁혀뒀던 탓에 이미
     * smallCategoryCodes에 넣어둔 시설 상당수(2,000여 건)가 대분류 게이트에서 먼저 걸려
     * 제외되는 버그가 있었다. 게다가 liked/similar는 시설을 이 테마의 categories가 아니라
     * "사용자가 좋아한 시설의 카테고리"로 먼저 걸러 가져오므로, 여기서 다시 categories를
     * 확인하면 호출부에 따라 결과가 달라지는 문제도 있었다. categories는 대신
     * {@link com.freepets.domain.course.service.CoursePresetService}가 DB 쿼리 1차 필터로만
     * 쓴다.
     */
    public boolean matchesFacilityDetail(Facility facility) {
        // Set.of(...)의 contains(null)은 예외를 던지므로(불변 컬렉션의 알려진 특성) 먼저 걸러낸다.
        String smallCategoryCode = facility.getSmallCategoryCode();
        return smallCategoryCode != null && smallCategoryCodes.contains(smallCategoryCode);
    }

}
