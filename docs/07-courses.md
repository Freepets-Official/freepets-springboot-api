# 07. 여행 코스 — 시작 (F3)

목적: 코스로 시작(프리셋/추천/직접) + 데려갈 아이 선택
DB: `courses`, `course_stops` — **신규 엔티티, 현재 코드에 전혀 없음**

## 데이터 모델 (신규)

### `courses`

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `course_id` | PK | |
| `user_id` | FK, nullable | PRESET은 null(운영자 소유), CUSTOM/RECOMMENDED는 요청자 소유 |
| `name` | varchar | |
| `description` | text | |
| `source` | enum(`CUSTOM`,`PRESET`,`RECOMMENDED`) | |
| `area` | varchar, nullable | PRESET 필터용 자유 텍스트 지역명(예: `"강릉"`) — 아래 "결정된 사항" 참고 |
| `created_at`/`updated_at` | `BaseEntity` 상속 | |

### `course_stops`

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | PK | |
| `course_id` | FK | |
| `facility_id` | FK | |
| `stop_order` | int (0부터) | 코스 내 순서. **시간은 저장하지 않고 08 조회 시 매번 계산** |

## 상수 (신규)

`LIKED_THRESHOLD = 6.5f` — "우리 아이 취향" 판정 만족도 임계값. 코드베이스 어디에도 없던 값이라 새로 정의 필요 (`CourseRecommendationService` 상수 또는 별도 Policy 클래스).

## `GET /api/v1/courses/presets`

- Query: `area` (자유 텍스트 지역명, 예: `"강릉"` — 아래 "결정된 사항" 참고)
- **DB 시딩 방식으로 결정** — 관광공사 API 실시간 연동 안 함. 운영자가 `courses(source=PRESET)` + `course_stops`를 미리 구성해두고 그대로 조회만 한다. `Facility.contentId`는 과거 1회성 데이터 시딩의 흔적으로만 남겨둔다.

**Response**
```json
[
  { "id": "preset-gangneung-sea", "name": "강릉 바다 산책 1일 코스", "description": "해변 산책으로 시작해...", "source": "PRESET", "stopIds": [1, 2, 5, 4] }
]
```

## `GET /api/v1/courses/recommended` (F3)

> **[정정]** `freepets-docs/docs/02-api-design.md` 확인 결과, `recommended-similar`는 별도 경로가 아니라
> **이 엔드포인트의 `mode` 쿼리 파라미터**다. 응답도 배열이 아니라 **단일 코스 객체**. 이전 버전(별도 경로 +
> 배열 응답)은 폐기.

- Query: `petIds`(콤마 구분), `mode`(`liked` | `similar`, 기본 `liked`)

### `mode=liked` — "우리 아이 취향 코스"

> **[정정]** 이전 버전은 "`petIds` 중 한 마리라도 6.5 이상이면 포함(union)" + "점수 desc 정렬"로 적었는데,
> 공식 스펙 원문은 **"만족도 평균 6.5+"** + **"카테고리 다양성·거리순"**이다. 아래로 교체.

1. 후보 산출: 시설별로 `petIds` 중 그 시설에 `PetSatisfaction` 기록이 있는 펫들만 골라 점수 **평균**을 낸다 (기록 없는 펫은 평균 계산에서 제외 — 전원 방문 기록을 요구하지 않음). 평균 `>= 6.5`(`LIKED_THRESHOLD`)인 시설만 후보로 남긴다.
2. distinct 후보 시설 수 `< 2` → **204 No Content**
3. 후보를 평균 점수 desc로 정렬한 뒤, 아래 "카테고리 다양성 + 거리순 조립" 공통 루틴에 넣어 최종 스톱 목록 확정 — **DB에 저장하지 않고 매 요청마다 재계산**

### `mode=similar` — "취향 비슷한 새 곳 탐험"

**공식 스펙(02-api-design.md) 기준 알고리즘**:
1. 취향 프로필 = 좋아한 곳들의 `category` 집합 + 그 곳들의 `review_tags` 집합
2. 후보 = 아직 방문(만족도 기록) 없는 시설 중 데려갈 아이들이 갈 수 있는 곳(판별 결과 ≠ DENIED)
3. 유사도 점수 = `카테고리 일치(가중 3)` + `리뷰 태그 겹침 수`
4. 후보를 유사도 점수 desc로 정렬한 뒤, 아래 "카테고리 다양성 + 거리순 조립" 공통 루틴에 넣어 최종 스톱 목록 확정

### 공통 루틴 — 카테고리 다양성 + 거리순 조립 (`CourseAssemblyService.assemble`)

`liked`·`similar` 둘 다 "정렬된 후보 → 최종 스톱"으로 가는 마지막 단계가 동일해서 하나의 메서드로 공유한다 (06/08에서 판별 로직을 하나로 공유한 것과 같은 이유 — 두 모드가 다른 결과를 내면 안 되는 공통 규칙이라).

```
입력: 점수(평균 만족도 or 유사도) desc 정렬된 후보 리스트
1. 앞에서부터 순회하며 카테고리별로 "그 카테고리에서 가장 점수 높은 1곳"만 채택 (같은 카테고리 중복 제외)
2. MAX_RECOMMENDED_STOPS(=4)개 찰 때까지, 또는 후보 소진될 때까지 반복
3. 채택된 스톱들을 최근접 이웃(nearest-neighbor) 방식으로 재정렬 — 점수 1위 시설을 시작점으로,
   매번 아직 안 고른 스톱 중 직전 스톱과 가장 가까운 곳을 다음으로 선택 (동선이 왔다갔다 하지 않게)
```

**신규 유틸 필요**: 3번 거리 계산에 haversine 기반 `GeoUtils.distanceMeters(lat1,lng1,lat2,lng2)` 필요 — `Facility.lat`/`lng`만 있고 현재 프로젝트에 이런 유틸이 없음. (08 문서에서 "Node B 소관이라 Spring A엔 불필요"라고 정리했던 것과 별개로, **07의 코스 조립 단계는 Spring A 소관이라 여기엔 필요함** — 헷갈리지 않게 주의.)

**[확장안, 팀 확정 전]** [`docs/planning/similar-course-scoring.md`](planning/similar-course-scoring.md)에 위 3번 스코어링을 훨씬 정교화한 초안이 있음 — 태그 그룹별 가중치(경험 ×2.0/일반 ×1.0) + kind·breedSize 보너스 + 리뷰 최신성 감쇠. 공식 스펙과 다른 팀원이 검토 전이라, 최종 채택 전까진 **공식 스펙(카테고리 가중3 + 태그 겹침수)을 기본으로 구현하고, 확장안은 팀 합의 후 교체**하는 걸 권장.

- 선행 스키마 변경 (Review 도메인, 두 알고리즘 공통으로 필요):
  - `Review`에 `tags`(신규, `review_tags` 테이블 또는 `@ElementCollection Set<ReviewTag>`)
  - `ReviewTag` enum: `LARGE_SPACE, LARGE_DOG, OFF_LEASED_AVAILABLE, PET_MENU_EXIST, STAFF_WELCOMING, WATER_EXIST, WASTE_BAG_PLACED, PARKING_CONVENIENT, QUIET_ATMOSPHERE, OUTDOOR`
  - 확장안 채택 시에만 추가로 필요: 태그 최소 3개 검증, `Review.visitedAt`
- 후보 판정: "안 가본 곳"은 `PetCheck`가 아니라 `PetSatisfaction`/`Review`(실제 방문 기록) 기준

**Response** (단일 객체, 조건 미달 시 **204 No Content**)
```json
{ "id": "recommended-similar", "name": "취향 비슷한 새 곳 탐험", "source": "RECOMMENDED", "stopFacilityIds": [12, 13, 14] }
```

## `GET /api/v1/courses` (내 코스)

- 로그인 사용자의 코스 목록(스톱·순서 포함). ⚠️ 페이지네이션 필요 여부 미결.

## `POST /api/v1/courses` — 코스 저장 (CUSTOM)

- Body: `name`, `stopFacilityIds`(순서 있는 배열)

## `PUT /api/v1/courses/{courseId}` — 코스 수정

- Body: POST와 동일 필드(이름·스톱·순서)

## `DELETE /api/v1/courses/{courseId}` — 코스 삭제

## 결정된 사항

1. **`area` 파라미터**: 관광공사 지역 코드 대신 **자유 텍스트 지역명**(`"강릉"` 등)을 쓴다. `Course.area varchar` 컬럼에 저장된 값과 단순 일치/포함 비교. 관광공사 API 실연동을 안 하기로 했으니 그쪽 코드 체계에 종속될 이유가 없다.
2. **`GET /api/v1/courses`(내 코스) 페이지네이션**: MVP는 페이지네이션 없이 flat list 반환. 사용자당 코스 수가 적어 트래픽 근거 없이 넣지 않는다. 필요해지면 `Pageable` 추가는 하위 호환되게 붙일 수 있음.
3. **recommended/recommended-similar 상위 N개**: `MAX_RECOMMENDED_STOPS = 4`로 고정 (상수화). 예시와 동일.

## DTO 시그니처

패키지: `com.freepets.domain.course.dto`

```java
public class CourseResponseDTO {
    public static class CoursePreview {
        private String id;                 // PRESET/RECOMMENDED는 문자열 id("preset-gangneung-sea" 등), CUSTOM은 courseId 문자열화
        private String name;
        private String description;
        private CourseSource source;       // CUSTOM, PRESET, RECOMMENDED
        private List<Long> stopIds;        // facilityId 순서 배열
        private List<StopMatchReason> matchReasons;  // recommended-similar 전용, 그 외 null
    }
    public static class StopMatchReason {   // similar-course-scoring.md 4절
        private Long facilityId;
        private List<ReviewTag> matchedTags;
        private boolean matchedByKind;
        private boolean matchedByBreedSize;
        private String reason;
    }
    public static class MyCourse {
        private Long courseId;
        private String name;
        private String description;
        private List<Long> stopIds;
        private LocalDateTime createdAt;
    }
}

public class CourseRequestDTO {
    public static class CreateRequest {    // 직접 만들기(CUSTOM), 08 문서 범위 밖이지만 courses 도메인 소속이라 같이 정의
        private String name;
        private String description;
        private List<Long> stopIds;
    }
}
```

- `GET /courses/presets` → `List<CoursePreview>`
- `GET /courses/recommended` → **단일 `CoursePreview`** (배열 아님, `mode` 무관하게 동일 타입). 조건 미달(대상 2곳 미만) 시 바디 없이 **204**
- `GET /courses` → `List<MyCourse>`
- `POST /courses` → `MyCourse`, `PUT /courses/{courseId}` → `MyCourse`
