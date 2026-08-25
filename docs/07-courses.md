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

## `GET /api/v1/courses/recommended` (mode=liked)

- Query: `petIds` (콤마 구분)
- 로직:
  1. `petIds` 각각에 대해 `PetSatisfaction.score >= 6.5`인 `facility` 조회, distinct
  2. distinct facility 수 `< 2` → **204 No Content**
  3. 점수 desc 상위 4곳 내외로 임시 `Course`(source=`RECOMMENDED`, id=`"recommended-liked"`)를 즉석 조립해 반환 — **DB에 저장하지 않고 매 요청마다 재계산**

## `GET /api/v1/courses/recommended-similar` (mode=similar)

- Query: `petIds`
- **[변경] 카테고리 근사 방식 폐기, 리뷰 태그 기반 가중 스코어링으로 확정.** 상세 알고리즘은 [`docs/planning/similar-course-scoring.md`](planning/similar-course-scoring.md) 참고. 임베딩/pgvector 없이 순수 가중합 연산이라 인프라 추가 없이 구현 가능.
- 선행 스키마 변경 (Review 도메인):
  - `Review`에 `tags`(신규 컬럼/테이블, `@ElementCollection Set<ReviewTag>` 또는 `review_tags` 조인 테이블) 추가
  - `ReviewTag` enum 신규: `LARGE_SPACE, LARGE_DOG, OFF_LEASED_AVAILABLE, PET_MENU_EXIST, STAFF_WELCOMING, WATER_EXIST, WASTE_BAG_PLACED`(경험, ×2.0) / `PARKING_CONVENIENT, QUIET_ATMOSPHERE, OUTDOOR`(일반, ×1.0)
  - 리뷰 작성 API(Request Body validation)에서 `tags` 최소 3개 필수 — 신규 리뷰부터 적용, 소급 없음
  - `Review`에 `visitedAt`(방문 시점) 컬럼 필요 — 최신성 가중치 계산용. 현재 엔티티에 없으면 추가
- 로직 (요약 — 전체는 planning 문서):
  1. liked 로직으로 "좋아한 곳" 집합 확보 (2곳 미만이면 여기서도 **204**) — `LIKED_THRESHOLD=6.5f` 그대로 재사용
  2. 좋아한 곳들에 **내가 남긴** 리뷰 태그 집합 = 취향 프로필
  3. 해당 pet의 `PetSatisfaction`/`Review` 기록이 **없는** facility 중 `petAllowed != NOT_ALLOWED`인 곳을 후보로, 후보에 달린 모든 리뷰에 대해 태그 겹침(그룹 가중치) × 최신성 × kind/breedSize 보너스를 합산한 시설 점수로 정렬해 상위 `MAX_RECOMMENDED_STOPS`개 선택
  4. 조건 충족 후보가 2곳 미만이면 **204**

**Response**: presets와 동일 형태 + `matchedTags`/`matchedByKind`/`matchedByBreedSize`/`reason`(추천 근거, planning 문서 4절 참고), `id: "recommended-similar"`, `source: "RECOMMENDED"`

## `GET /api/v1/courses` (내 코스)

- 로그인 사용자의 `source=CUSTOM` 코스 목록. ⚠️ 페이지네이션 필요 여부 미결.

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

- `GET /courses/presets`, `GET /courses/recommended`, `GET /courses/recommended-similar` → `List<CoursePreview>` (recommended류는 조건 미달 시 바디 없이 204)
- `GET /courses` → `List<MyCourse>`
