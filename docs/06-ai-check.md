# 06. 낱개 판별 (신규 — 코스 판별의 선행 조건)

> 08(코스 판별)이 "낱개 `ai/check`와 동일 규칙 재사용"을 전제하는데, 코드베이스 조사 결과
> `POST /api/v1/ai/check`도 판별 로직도 아직 존재하지 않는다. 이 문서에서 먼저 확정하고,
> 07·08은 이 문서의 `PetCheckService.check(...)`를 그대로 호출한다.

## 판별 아키텍처 — 하이브리드 (LLM 오프라인 파싱 + 규칙 엔진 런타임)

`PetCheck.model` 컬럼과 `Facility.petConditionRaw`(AI가 파싱한 원문 정책 텍스트로 추정)의 존재로 볼 때,
원 설계 의도는 "매 요청마다 LLM 호출"이 아니라 **LLM은 시설 데이터 적재 시점에 원문을 구조화하는 데만 쓰고,
사용자가 체감하는 판별 자체(`ai/check`, `ai/course-check`)는 이미 구조화된 데이터로 빠르게 규칙 계산**하는 쪽으로 판단했다.
체중 초과·예방접종 같은 **안전 관련 필수 조건은 LLM 추론에 맡기지 않는다.**

```
[시설 등록/수정] --petConditionRaw--> [오프라인 파싱, LLM 1회] --> CheckList / maxWeight / petAllowed(baseline)
                                                                          │
[사용자 ai/check 요청] ------------------------------------------------> [런타임 규칙 엔진] --> ALLOWED/CONDITIONAL/NOT_ALLOWED
                                                                     (LLM 호출 없음, DB 조회만)
```

### 1) 오프라인 파싱 — `FacilityConditionParsingService` (신규)

- 트리거: `Facility` 생성 또는 `petConditionRaw` 수정 시 1회 실행 (관리자 등록 플로우 훅, 또는 배치)
- 입력: `petConditionRaw`(원문 텍스트)
- LLM에 JSON 스키마를 강제해 아래를 추출 (Tripial의 `response_mime_type="application/json"` + 마크다운 펜스 스트립 패턴 참고):
  ```json
  { "maxWeight": 25.0, "petAllowed": "CONDITIONAL", "checkList": [{ "type": "LEASH_REQUIRED", "isChecked": true }] }
  ```
- 결과를 `Facility.maxWeight`/`petAllowed`/`CheckList` 로우에 반영
- 파싱 실패·불확실 → `petAllowed = UNKNOWN`, checklist 비움, `Facility.needsManualReview = true`(신규 컬럼)로 표시해 운영자 확인 유도
- LLM 제공자는 인터페이스(`FacilityConditionParser`)로 추상화해 구현체 교체 가능하게 (Claude/Gemini 등 특정 벤더 고정 안 함)
- `PetCheck.model`은 매 판별 시점의 "판정에 사용된 시설 데이터가 어떤 모델로 파싱됐는지" 스냅샷 기록 용도로 재정의 (런타임 규칙 엔진 자체는 LLM을 안 쓰므로, 여기 저장되는 값은 규칙 엔진 버전이 아니라 그 시설의 마지막 오프라인 파싱 모델명)

### 2) 런타임 판별 — 아래 "판별 알고리즘" 그대로, LLM 미호출

## 사용하는 기존 데이터

| 소스 | 필드 | 용도 |
|---|---|---|
| `Pet` | `weight`(BigDecimal), `isVaccinated`, `breedSize` | 반려동물 쪽 판별 조건 |
| `Facility` | `petAllowed`(ALLOWED/CONDITIONAL/NOT_ALLOWED/UNKNOWN), `maxWeight`, `petConditionRaw` | 시설 정책 |
| `CheckList` / `CheckListType` | `facility` FK, `type`, `isChecked` | 시설별 조건 항목 (LEASH_REQUIRED, CAGE_REQUIRED, WEIGHT_LIMIT, VACCINATION_REQUIRED, INDOOR_ALLOWED, OUTDOOR_ONLY, ADDITIONAL_FEE, BREED_RESTRICTION) |

신규 테이블/컬럼 추가 없음 — 기존 엔티티만으로 구현 가능.

## 판별 알고리즘 — `PetCheckService.check(petIds, facilityId)`

**낱개(06)와 코스(08)가 공통으로 호출하는 단 하나의 메서드.** 반려동물 1마리씩 아래 순서로 평가:

1. `facility.petAllowed == NOT_ALLOWED` → **NOT_ALLOWED**, reason = "이 장소는 반려동물 동반이 불가능해요"
2. `facility.maxWeight != null && pet.weight > facility.maxWeight` → **NOT_ALLOWED**, reason = "{pet.name}({pet.weight}kg)이 체중 제한({facility.maxWeight}kg)을 초과해요"
3. `CheckListType.VACCINATION_REQUIRED`가 `isChecked=true`인데 `pet.isVaccinated == false` → **NOT_ALLOWED**, reason = "예방접종 확인이 안 된 아이는 입장이 어려워요"
4. 그 외 `isChecked=true`인 체크리스트 항목이 하나라도 있으면 → **CONDITIONAL**, `conditions`에 해당 타입 나열
5. `facility.petAllowed == UNKNOWN` && 체크리스트 없음 && `petConditionRaw` 없음 → **UNKNOWN**, reason = "아직 확인된 정보가 없어요"
6. 위 전부 해당 없음 → **ALLOWED**

**그룹(펫 여러 마리) 종합**: 심각도 `NOT_ALLOWED(3) > UNKNOWN(2) > CONDITIONAL(1) > ALLOWED(0)` 중 최악값을 `overall`로 사용. 08의 "블락 여부" 판정도 이 규칙을 그대로 재사용한다.

**checklist**: 해당 시설의 `CheckList`를 `{type, label, isChecked}`로 매핑 (label은 CheckListType → 한글 문구 상수 테이블).

**tips**: `isChecked=true`인 항목만 자연어 안내문으로 변환.

```
LEASH_REQUIRED        → "리드줄이 필요해요"
CAGE_REQUIRED         → "이동장이 필요해요"
WEIGHT_LIMIT          → "체중 제한이 있어요"
VACCINATION_REQUIRED  → "예방접종 확인이 필요해요"
INDOOR_ALLOWED        → "실내 동반 가능해요"
OUTDOOR_ONLY          → "실외 공간만 가능해요"
ADDITIONAL_FEE        → "추가 요금이 있을 수 있어요"
BREED_RESTRICTION     → "견종 제한이 있을 수 있어요"
```

## `POST /api/v1/ai/check`

**Request**
```json
{ "petIds": [1, 2], "facilityId": 1 }
```

**Response**
```json
{
  "facility": { "facilityId": 1, "name": "안목해변 솔숲 산책로", "category": "PARK", "distanceM": null },
  "verdicts": [
    { "petId": 1, "petName": "몽이", "result": "ALLOWED", "reason": "리드줄만 착용하면 문제 없어요", "conditions": ["LEASH_REQUIRED"] },
    { "petId": 2, "petName": "보리", "result": "NOT_ALLOWED", "reason": "보리(27.5kg)이 체중 제한(25kg)을 초과해요", "conditions": [] }
  ],
  "overall": "NOT_ALLOWED",
  "checklist": [
    { "type": "LEASH_REQUIRED", "label": "리드줄 필수", "isChecked": true }
  ],
  "tips": ["리드줄이 필요해요"]
}
```

**결정**: 원본 스펙 예시의 `category: "TOUR"`는 채택하지 않는다. `FacilityCategory` enum(CAFE/RESTAURANT/ACCOMMODATION/PARK/HOSPITAL/GROOMING/SHOPPING_MALL/ETC)을 그대로 쓰고, 산책로류는 `PARK`로 매핑한다 — enum에 값을 추가하면 대체 시설 매칭(08)의 카테고리 동질성 조건 범위가 애매해져서 기존 8종을 유지하는 쪽이 낫다.

## DTO 시그니처 (CLAUDE.md 컨벤션 — 도메인당 XxxRequestDTO/XxxResponseDTO, 내부 static class는 DTO 접미사 없음)

패키지: `com.freepets.domain.petcheck.dto`

```java
public class PetCheckRequestDTO {
    public static class CheckRequest {
        private List<Long> petIds;
        private Long facilityId;
    }
}

public class PetCheckResponseDTO {
    public static class CheckResult {
        private FacilitySummary facility;
        private List<PetVerdict> verdicts;
        private PetCheckResult overall;      // 06의 그룹 종합 규칙 적용
        private List<ChecklistItem> checklist;
        private List<String> tips;
    }
    public static class FacilitySummary {
        private Long facilityId;
        private String name;
        private FacilityCategory category;
        private Integer distanceM;           // 코스 컨텍스트가 아니면 null
    }
    public static class PetVerdict {
        private Long petId;
        private String petName;
        private PetCheckResult result;
        private String reason;
        private List<CheckListType> conditions;
    }
    public static class ChecklistItem {
        private CheckListType type;
        private String label;
        private boolean isChecked;
    }
}
```

`PetCheckService.check(List<Long> petIds, Long facilityId)`가 `CheckResult`를 반환 — 06/08 양쪽 컨트롤러가 이 하나의 서비스 메서드만 호출한다.

## 에러코드 제안 (`ErrorStatus`에 `// AI 판별 에러` 블록 추가)

| 코드 | HTTP | 설명 |
|---|---|---|
| `AI4001` | 404 | PET_NOT_FOUND |
| `AI4002` | 404 | FACILITY_NOT_FOUND |
| `AI4003` | 400 | EMPTY_PET_IDS |

## 참고: 판별 이력 저장

`PetCheck` 엔티티(= `pet_checks` 테이블)가 이미 있으니 매 판별 결과를 pet×facility 단위로 적재해두면 좋음 (감사 로그, 추후 "이미 판별해본 곳" 필터 등). 다만 **07의 similar 추천에서 "안 가본 곳" 판정은 `PetCheck`가 아니라 `PetSatisfaction`/`Review`(실제 방문 기록) 기준**으로 한다 — 판별만 해본 것과 실방문은 다르므로.
