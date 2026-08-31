package com.freepets.domain.facility.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.entity.Requirement;

/**
 * 관광공사 반려동물 동반 조건 원문에서 판정값과 요구조건을 뽑아낸다.
 *
 * <p>AI를 쓰지 않고 사전 매핑과 키워드로만 처리한다. 실데이터 9,694건을 조사한 결과
 * {@code acmpyNeedMtr}의 원자값이 7개, {@code acmpyTypeCd}가 2개뿐인 코드성 필드라
 * 자연어 이해가 필요하지 않았다. 같은 입력이 항상 같은 결과를 내고 단위 테스트가 가능하다.
 *
 * <p>이 클래스는 관광공사 반려동물 목록에 <b>등재된 시설에 대해서만</b> 호출된다.
 * 미등재 시설은 호출 없이 {@link PetAllowed#PENDING}으로 처리한다.
 */
@Component
public class PetConditionParser {

    /**
     * 파싱 규칙 버전. 규칙을 바꾸면 올려서 전량 재파싱 대상으로 만든다.
     *
     * <p>2 — maxWeightInclusive(#43) 추가로 WEIGHT_LIMIT 정규식이 바뀌었다. 다만 지금은
     * parserVersion을 실제로 읽어 재파싱 대상을 고르는 로직이 없어서, 이 값을 올리는 것만으로
     * 기존 시설의 maxWeightInclusive가 저절로 채워지지는 않는다 — 원문(TourAPI 텍스트)이
     * 실제로 바뀌어 재동기화될 때만 규칙 엔진이 다시 돈다({@code Facility#updateFromTourApi}
     * 참고). 기존 데이터 백필 여부는 별도 결정 사항이다.
     */
    public static final int PARSER_VERSION = 2;

    /**
     * {@code acmpyNeedMtr}의 원자값 사전. 실데이터에서 관측된 값이 전부 여기 있다.
     *
     * <p>{@code 자유이용}과 {@code 기타}는 특정 요구조건으로 옮길 수 없어 매핑하지 않는다.
     * 키는 공백을 제거해 비교하므로 표기 흔들림에 영향을 받지 않는다.
     */
    private static final Map<String, Requirement> REQUIRED_MATTER_DICTIONARY = new LinkedHashMap<>();

    static {
        REQUIRED_MATTER_DICTIONARY.put("목줄착용", Requirement.LEASH);
        REQUIRED_MATTER_DICTIONARY.put("이동장(켄넬)사용", Requirement.CAGE);
        REQUIRED_MATTER_DICTIONARY.put("입마개착용", Requirement.MUZZLE);
        REQUIRED_MATTER_DICTIONARY.put("반려동물유모차탑승", Requirement.STROLLER);
        REQUIRED_MATTER_DICTIONARY.put("매너벨트착용", Requirement.MANNER_BELT);
    }

    /** 안내견만 허용하는 시설. 사실상 일반 반려동물 동반이 불가하다. */
    private static final Pattern GUIDE_DOG_ONLY =
            Pattern.compile("^(시각\\s*장애인\\s*|맹인\\s*)?안내견$");

    private static final List<String> GUIDE_DOG_ONLY_KEYWORDS =
            List.of("안내견만", "도우미견만", "보조견만");

    private static final Pattern VACCINATION_KEYWORD =
            Pattern.compile("예방\\s*접종|광견병\\s*접종|접종\\s*완료|접종.{0,6}필수");

    private static final Pattern SMALL_DOG_KEYWORD = Pattern.compile("소형견");

    /** {@code 중소형견}·{@code 중, 소형견}처럼 중형견을 포함하면 소형견 한정이 아니다. */
    private static final Pattern MEDIUM_DOG_INCLUDED =
            Pattern.compile("중\\s*[,/·]?\\s*소형견|중소형견");

    /** 야외 한정은 근거가 분명할 때만 붙인다. {@code 일부구역 동반가능}만으로는 단정하지 않는다. */
    private static final Pattern OUTDOOR_ONLY_KEYWORD =
            Pattern.compile("(야외|실외|테라스|옥외)\\s*(공간|구역|좌석|석)?\\s*(에서만|에 한|만|한해|한정)");

    /**
     * 체중 상한. {@code 이상}·{@code 초과}는 상한이 아니므로 매칭하지 않는다. 경계 포함 여부를
     * 가르는 표현(이하/까지/초과 불가 = 포함, 미만 = 제외)을 2번째 그룹으로 따로 잡는다.
     */
    private static final Pattern WEIGHT_LIMIT = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:kg|㎏|킬로그램|킬로)\\s*(이하|미만|까지|초과\\s*불가)"
    );

    /** {@code 미만}만 경계를 제외한다. 나머지("이하", "까지", "초과 불가")는 그 체중까지 포함이다. */
    private static final String EXCLUSIVE_BOUNDARY_WORD = "미만";

    private static final BigDecimal MAX_REASONABLE_WEIGHT = BigDecimal.valueOf(200);

    public PetConditionParseResult parse(
            String accompanyType,
            String allowedAnimalText,
            String requiredMatterText,
            String etcAccompanyText,
            String accidentRiskText
    ) {
        String allowedAnimal = PetConditionNormalizer.normalize(allowedAnimalText);
        String requiredMatter = PetConditionNormalizer.normalize(requiredMatterText);
        String freeText = String.join(" ",
                allowedAnimal,
                PetConditionNormalizer.normalize(etcAccompanyText),
                PetConditionNormalizer.normalize(accidentRiskText)
        ).trim();

        if (isDenied(allowedAnimal)) {
            return new PetConditionParseResult(PetAllowed.DENIED, null, null, List.of());
        }

        // "안내견만 가능"류는 "불가"처럼 완전히 거부하는 게 아니라 조건부 예외라 DENIED로
        // 단정하지 않는다 — ALLOWED로 단정해서 일반 반려동물이 통과되는 것도 위험하므로,
        // PetCheckJudgeService가 이미 "확인 필요"로 안내하는 PENDING을 그대로 쓴다. 원문
        // 자체는 있으니 이후 LLM 조건 파싱 배치는 정상적으로 돌고, 스키마에 담을 컬럼이 없는
        // "안내견만 가능" 문장은 unmappedConditionText로 남아 AMBIGUOUS가 된다 — 사람이
        // 검토할 신호가 된다.
        if (isGuideDogOnly(allowedAnimal)) {
            return new PetConditionParseResult(PetAllowed.PENDING, null, null, List.of());
        }

        WeightLimit weightLimit = extractMaxWeight(freeText);

        return new PetConditionParseResult(
                PetAllowed.ALLOWED,
                weightLimit.value(),
                weightLimit.inclusive(),
                extractRequirements(requiredMatter, freeText)
        );
    }

    /**
     * 원문에 명시적 불가 표현이 있는지 본다.
     *
     * <p>실데이터에서 {@code 불가}, {@code 불가(보조견만 가능)} 형태로 나타난다. "맹인 안내견",
     * "안내견만 가능"처럼 안내견 전용 예외를 나타내는 표현은 {@link #isGuideDogOnly}가 따로
     * 다룬다 — 완전 거부가 아니라 조건부 예외이기 때문이다.
     */
    private boolean isDenied(String allowedAnimal) {
        return !allowedAnimal.isEmpty() && allowedAnimal.startsWith("불가");
    }

    private boolean isGuideDogOnly(String allowedAnimal) {
        if (GUIDE_DOG_ONLY.matcher(allowedAnimal).matches()) {
            return true;
        }
        return GUIDE_DOG_ONLY_KEYWORDS.stream().anyMatch(allowedAnimal::contains);
    }

    private List<Requirement> extractRequirements(
            String requiredMatter,
            String freeText
    ) {
        List<Requirement> requirements = new ArrayList<>();

        for (String atom : requiredMatter.split(",")) {
            Requirement requirement = REQUIRED_MATTER_DICTIONARY.get(atom.replaceAll("\\s+", ""));
            if (requirement != null && !requirements.contains(requirement)) {
                requirements.add(requirement);
            }
        }

        if (VACCINATION_KEYWORD.matcher(freeText).find()) {
            addIfAbsent(requirements, Requirement.VACCINATION);
        }
        if (isSmallDogOnly(freeText)) {
            addIfAbsent(requirements, Requirement.SMALL_ONLY);
        }
        if (OUTDOOR_ONLY_KEYWORD.matcher(freeText).find()) {
            addIfAbsent(requirements, Requirement.OUTDOOR_ONLY);
        }

        return requirements;
    }

    private boolean isSmallDogOnly(String freeText) {
        return SMALL_DOG_KEYWORD.matcher(freeText).find()
                && !MEDIUM_DOG_INCLUDED.matcher(freeText).find();
    }

    /**
     * 체중 상한과 경계 포함 여부를 뽑는다. 여러 값이 나오면 가장 엄격한 값(가장 작은 상한)을
     * 택하고, 그 값의 경계 표현("이하"/"미만"/"까지"/"초과 불가")을 그대로 따른다 — 값이 같아도
     * 표현이 다르면 경계 취급이 달라지므로 값과 경계를 항상 같이 다룬다.
     *
     * <p>{@code 체고 40cm}처럼 단위가 다른 값은 패턴에 걸리지 않는다.
     */
    private WeightLimit extractMaxWeight(String freeText) {
        Matcher matcher = WEIGHT_LIMIT.matcher(freeText);
        WeightLimit strictest = null;

        while (matcher.find()) {
            BigDecimal candidate = new BigDecimal(matcher.group(1));
            boolean isReasonable = candidate.signum() > 0
                    && candidate.compareTo(MAX_REASONABLE_WEIGHT) < 0;
            if (!isReasonable) {
                continue;
            }
            if (strictest == null || candidate.compareTo(strictest.value()) < 0) {
                boolean inclusive = !EXCLUSIVE_BOUNDARY_WORD.equals(matcher.group(2));
                strictest = new WeightLimit(candidate, inclusive);
            }
        }
        return strictest == null ? WeightLimit.EMPTY : strictest;
    }

    /** {@code value}가 {@code null}이면 {@code inclusive}도 항상 {@code null}이어야 한다. */
    private record WeightLimit(
            BigDecimal value,
            Boolean inclusive
    ) {
        private static final WeightLimit EMPTY = new WeightLimit(null, null);
    }

    private void addIfAbsent(
            List<Requirement> requirements,
            Requirement requirement
    ) {
        if (!requirements.contains(requirement)) {
            requirements.add(requirement);
        }
    }

}
