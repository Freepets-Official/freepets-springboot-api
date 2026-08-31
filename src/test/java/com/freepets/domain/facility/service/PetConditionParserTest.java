package com.freepets.domain.facility.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;

/**
 * 파싱 규칙 검증. 입력값은 모두 관광공사 실데이터 9,694건에서 관측된 원문이다.
 */
class PetConditionParserTest {

    private final PetConditionParser petConditionParser = new PetConditionParser();

    private PetConditionParseResult parse(
            String allowedAnimal,
            String requiredMatter
    ) {
        return petConditionParser.parse("전구역 동반가능", allowedAnimal, requiredMatter, "", "");
    }

    // ------------------------------------------------------------------
    // petAllowed 판정
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"불가", "불가(보조견만 가능)"})
    @DisplayName("원문에 명시적 불가 표현이 있으면 DENIED로 판정한다")
    void 명시적_불가_표현이_있으면_DENIED로_판정한다(String allowedAnimal) {
        PetConditionParseResult result = parse(allowedAnimal, "");

        assertThat(result.petAllowed()).isEqualTo(PetAllowed.DENIED);
        assertThat(result.requirements()).isEmpty();
        assertThat(result.maxWeight()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "맹인 안내견",
            "시각 장애인 안내견",
            "안내견",
            "안내견만 가능",
            "안내견만 가"
    })
    @DisplayName("안내견 전용 예외 표현은 완전 거부가 아니라 조건부 예외라 PENDING으로 판정한다")
    void 안내견_전용_표현은_PENDING으로_판정한다(String allowedAnimal) {
        // "불가"와 달리 조건부 예외라 DENIED로 단정하지 않는다 — 그렇다고 ALLOWED로 단정하면
        // 일반 반려동물이 체중/요구조건만 통과해 들어갈 수 있다고 잘못 안내하게 된다.
        // PetCheckJudgeService가 PENDING을 "확인 필요"로 안내하므로 안전하다.
        PetConditionParseResult result = parse(allowedAnimal, "");

        assertThat(result.petAllowed()).isEqualTo(PetAllowed.PENDING);
        assertThat(result.requirements()).isEmpty();
        assertThat(result.maxWeight()).isNull();
    }

    @Test
    @DisplayName("동반 정보가 있으면 조건이 비어 있어도 ALLOWED로 판정한다")
    void 동반_정보가_있으면_조건이_비어도_ALLOWED로_판정한다() {
        PetConditionParseResult result = parse("", "");

        assertThat(result.petAllowed()).isEqualTo(PetAllowed.ALLOWED);
    }

    @Test
    @DisplayName("전 견종 동반 가능은 ALLOWED로 판정한다")
    void 전_견종_동반_가능은_ALLOWED로_판정한다() {
        PetConditionParseResult result = parse("전 견종 동반 가능", "목줄 착용");

        assertThat(result.petAllowed()).isEqualTo(PetAllowed.ALLOWED);
    }

    // ------------------------------------------------------------------
    // requirements 사전 매핑
    // ------------------------------------------------------------------

    @Test
    @DisplayName("필요사항 원문을 콤마로 나눠 요구조건으로 옮긴다")
    void 필요사항_원문을_콤마로_나눠_요구조건으로_옮긴다() {
        PetConditionParseResult result = parse("전 견종 동반 가능", "입마개 착용,목줄 착용,이동장(켄넬)사용");

        assertThat(result.requirements())
                .containsExactlyInAnyOrder(Requirement.MUZZLE, Requirement.LEASH, Requirement.CAGE);
    }

    @Test
    @DisplayName("유모차와 매너벨트도 요구조건으로 옮긴다")
    void 유모차와_매너벨트도_요구조건으로_옮긴다() {
        PetConditionParseResult result =
                parse("전 견종 동반 가능", "목줄 착용,반려동물 유모차 탑승,매너벨트 착용");

        assertThat(result.requirements())
                .containsExactlyInAnyOrder(
                        Requirement.LEASH, Requirement.STROLLER, Requirement.MANNER_BELT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"자유이용", "기타", "자유이용,기타"})
    @DisplayName("자유이용과 기타는 특정 요구조건으로 옮기지 않는다")
    void 자유이용과_기타는_요구조건으로_옮기지_않는다(String requiredMatter) {
        PetConditionParseResult result = parse("전 견종 동반 가능", requiredMatter);

        assertThat(result.requirements()).isEmpty();
    }

    @Test
    @DisplayName("같은 요구조건이 중복돼도 한 번만 담는다")
    void 같은_요구조건이_중복돼도_한_번만_담는다() {
        PetConditionParseResult result = parse("전 견종 동반 가능", "목줄 착용,목줄착용");

        assertThat(result.requirements()).containsExactly(Requirement.LEASH);
    }

    // ------------------------------------------------------------------
    // 조건부 요구사항 함정
    // ------------------------------------------------------------------

    @Test
    @DisplayName("맹견에만 걸리는 입마개 조건은 요구조건에 넣지 않는다")
    void 맹견에만_걸리는_입마개_조건은_요구조건에_넣지_않는다() {
        PetConditionParseResult result =
                parse("전 견종 출입 가능(맹견의 경우, 입마개 착용 필수)", "목줄 착용");

        assertThat(result.requirements())
                .containsExactly(Requirement.LEASH)
                .doesNotContain(Requirement.MUZZLE);
    }

    // ------------------------------------------------------------------
    // 자유 텍스트 추출
    // ------------------------------------------------------------------

    @Test
    @DisplayName("예방접종 언급이 있으면 접종 요구조건을 붙인다")
    void 예방접종_언급이_있으면_접종_요구조건을_붙인다() {
        PetConditionParseResult result =
                parse("맹견 제외 15kg 이하 예방 접종 완료한 전 견종 동반 가능", "목줄 착용");

        assertThat(result.requirements()).contains(Requirement.VACCINATION);
    }

    @Test
    @DisplayName("소형견 한정이면 소형견 요구조건을 붙인다")
    void 소형견_한정이면_소형견_요구조건을_붙인다() {
        PetConditionParseResult result = parse("소형견만 동반 가능", "");

        assertThat(result.requirements()).contains(Requirement.SMALL_ONLY);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "중소형견 동반 가능",
            "중, 소형견 (10kg 이하)",
            "중/소형견 10kg 미만만 입실 가능"
    })
    @DisplayName("중형견을 포함하면 소형견 한정으로 보지 않는다")
    void 중형견을_포함하면_소형견_한정으로_보지_않는다(String allowedAnimal) {
        PetConditionParseResult result = parse(allowedAnimal, "");

        assertThat(result.requirements()).doesNotContain(Requirement.SMALL_ONLY);
    }

    // ------------------------------------------------------------------
    // maxWeight 추출
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "10kg 이하 동반 가능",
            "10kg이하 소형견",
            "10kg 미만",
            "10㎏ 미만 동반 가능",
            "맹견 제외 10kg 이하 동반 가능"
    })
    @DisplayName("체중 상한 표현에서 숫자를 뽑는다")
    void 체중_상한_표현에서_숫자를_뽑는다(String allowedAnimal) {
        PetConditionParseResult result = parse(allowedAnimal, "");

        assertThat(result.maxWeight()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    @DisplayName("체중 상한이 여러 개면 가장 엄격한 값을 택한다")
    void 체중_상한이_여러_개면_가장_엄격한_값을_택한다() {
        PetConditionParseResult result =
                parse("소형견(10kg이하) 한마리 동반 가능(두마리일 경우 합쳐서 8kg이하)", "");

        assertThat(result.maxWeight()).isEqualByComparingTo(BigDecimal.valueOf(8));
    }

    @Test
    @DisplayName("체고 같은 다른 단위는 체중으로 잡지 않는다")
    void 체고_같은_다른_단위는_체중으로_잡지_않는다() {
        PetConditionParseResult result =
                parse("맹견 제외 체중 12kg 미만, 체고 40cm 미만의 전 견종 동반 가능", "");

        assertThat(result.maxWeight()).isEqualByComparingTo(BigDecimal.valueOf(12));
    }

    @Test
    @DisplayName("이상·초과는 상한이 아니므로 체중을 뽑지 않는다")
    void 이상이나_초과는_상한이_아니므로_체중을_뽑지_않는다() {
        PetConditionParseResult result = parse("10kg 이상 대형견 환영", "");

        assertThat(result.maxWeight()).isNull();
    }

    @Test
    @DisplayName("체중 언급이 없으면 상한은 비어 있다")
    void 체중_언급이_없으면_상한은_비어_있다() {
        PetConditionParseResult result = parse("전 견종 동반 가능", "목줄 착용");

        assertThat(result.maxWeight()).isNull();
    }

    // ------------------------------------------------------------------
    // 정규화
    // ------------------------------------------------------------------

    @Test
    @DisplayName("HTML 태그와 엔티티가 섞여 있어도 값을 읽어낸다")
    void HTML_태그와_엔티티가_섞여_있어도_값을_읽어낸다() {
        PetConditionParseResult result = parse("<p>10kg 이하&nbsp;동반 가능</p>", "목줄 착용");

        assertThat(result.maxWeight()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(result.requirements()).contains(Requirement.LEASH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-", "없음", "해당없음", "정보없음", "N/A", "   "})
    @DisplayName("미기재 표현은 값이 없는 것으로 본다")
    void 미기재_표현은_값이_없는_것으로_본다(String rawValue) {
        assertThat(PetConditionNormalizer.isBlank(rawValue)).isTrue();
    }

}
