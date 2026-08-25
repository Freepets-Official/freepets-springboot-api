# 08. 코스 판별 결과 + 대체 제안 (F3)

목적: 코스 전체 타임라인 판별. 막힌 스톱에 대체 시설 제안·스왑
DB: `courses`/`course_stops`(구성만 저장, 판별은 조회 시 재계산)

## 흐름 — `POST /api/v1/ai/course-check`

**Request**
```json
{ "stopIds": [1, 2, 5, 4], "petIds": [1, 2] }
```

1. `stopIds` 순서대로 순회하며, 각 스톱마다 **06의 `PetCheckService.check(petIds, facilityId)`를 그대로 호출** (낱개와 다른 규칙 절대 금지)
2. `time = "10:00" + stopOrder * 90분` (HH:mm 포맷, stopOrder는 배열 내 인덱스)
3. `group.overall == NOT_ALLOWED`인 스톱만 대체 탐색 (동적 쿼리 — `AlternativeFacility` 테이블 사용 안 함):
   - 후보 = `Facility.category == 원본.category` AND `facilityId NOT IN (코스에 이미 포함된 시설)` AND `petAllowed != NOT_ALLOWED` AND (`maxWeight IS NULL` OR `maxWeight >= 대상 pet 중 최대 체중`)
   - 각 후보에 `PetCheckService.check` 재적용해 `overall`이 `ALLOWED`/`CONDITIONAL`인 것만 필터
   - 남은 후보를 원본 시설과의 거리 오름차순 정렬 후 1위 채택 (거리 계산은 아래 `GeoUtils` 참고)
   - 후보 없음 → `alternative: null` → FE가 "이 스톱은 빼는 걸 권장"으로 표기 (백엔드는 문구를 만들지 않는다 — 억지 대안 금지 원칙)
4. 코스 전체 `overall` = 모든 스톱 `overall` 중 최악값 (06과 동일 심각도 규칙: `NOT_ALLOWED > UNKNOWN > CONDITIONAL > ALLOWED`)
5. `blockedCount` = `overall == NOT_ALLOWED`인 스톱 개수

**Response**
```json
{
  "stops": [
    {
      "facility": { "facilityId": 1, "name": "안목해변 솔숲 산책로", "category": "PARK", "distanceM": 820 },
      "time": "10:00",
      "group": {
        "verdicts": [{ "petId": 1, "result": "ALLOWED", "reason": "...", "conditions": [] }],
        "overall": "ALLOWED", "checklist": [], "tips": []
      },
      "alternative": null
    },
    {
      "facility": { "facilityId": 2, "name": "카페 파도살롱", "category": "CAFE", "distanceM": 1500 },
      "time": "11:30",
      "group": {
        "verdicts": [
          { "petId": 1, "result": "ALLOWED", "reason": "...", "conditions": [] },
          { "petId": 2, "result": "NOT_ALLOWED", "reason": "보리(27.5kg)이 체중 제한(25kg)을 초과해요", "conditions": [] }
        ],
        "overall": "NOT_ALLOWED", "checklist": [], "tips": []
      },
      "alternative": { "facilityId": 6, "name": "헤이도그 애견호텔&카페", "distanceKm": 0.4 }
    }
  ],
  "overall": "NOT_ALLOWED",
  "blockedCount": 1
}
```

## DTO 시그니처

패키지: `com.freepets.domain.course.dto`

```java
public class CourseCheckRequestDTO {
    public static class CheckRequest {
        private List<Long> stopIds;    // facilityId 배열, 순서 = 방문 순서
        private List<Long> petIds;
    }
}

public class CourseCheckResponseDTO {
    public static class CheckResult {
        private List<StopResult> stops;
        private PetCheckResult overall;   // 전체 스톱 중 최악값
        private int blockedCount;
    }
    public static class StopResult {
        private PetCheckResponseDTO.FacilitySummary facility;  // 06 DTO 재사용
        private String time;              // "HH:mm"
        private PetCheckResponseDTO.CheckResult group;         // 06 DTO 재사용 (facility 필드는 중복이라 생략 가능)
        private AlternativeFacility alternative;  // null 가능
    }
    public static class AlternativeFacility {
        private Long facilityId;
        private String name;
        private Double distanceKm;
    }
}
```

`CourseCheckService.check(stopIds, petIds)`는 내부에서 `PetCheckService.check(petIds, stopId)`를 스톱마다 호출 — 06과 완전히 동일한 반환 타입(`PetCheckResponseDTO.CheckResult`)을 그대로 얹어서 쓰므로 낱개·코스 결과가 절대 어긋날 수 없는 구조.

## 거리 계산 — `GeoUtils` (신규 유틸, 현재 프로젝트에 없음)

`Facility.lat`/`lng`(BigDecimal)로 haversine 공식을 사용해 미터 단위 거리 계산. `com.freepets.global.util.GeoUtils.distanceMeters(lat1, lng1, lat2, lng2)` 형태로 공용화해 07의 similar 추천 거리 가중치에서도 재사용.

## 에러코드 제안

| 코드 | HTTP | 설명 |
|---|---|---|
| `COURSE4001` | 404 | STOP_NOT_FOUND |
| `COURSE4002` | 400 | EMPTY_STOPS |
| `AI4001` | 404 | PET_NOT_FOUND (06과 공유) |

## 구현 순서 제안 (06/07/08 통틀어)

1. `GeoUtils.distanceMeters` — 공용 유틸
2. `FacilityConditionParsingService` (06 오프라인 LLM 파싱) — `Facility.petConditionRaw` → `maxWeight`/`petAllowed`/`CheckList` 구조화. 인터페이스로 추상화해 LLM 제공자는 나중에 결정
3. `PetCheckService.check` (06 런타임 규칙 엔진, LLM 미호출) + `POST /api/v1/ai/check` 컨트롤러
4. `Course`/`CourseStop` 엔티티 + `CourseRepository`
5. `CourseQueryService` — presets / recommended / recommended-similar / 내 코스
6. `CourseCheckService` — 3번의 `PetCheckService`를 순회 호출 + 시간 계산 + 대체 탐색
7. `CourseController` 배선 (경로: `/api/v1/courses/*`, `/api/v1/ai/course-check`)
