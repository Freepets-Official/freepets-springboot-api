# 06. 판별 기록 (Pet Checks) — Spring A 소관 범위

> **[정정]** 이 문서의 이전 버전은 `POST /api/v1/ai/check` 판별 로직(규칙 엔진/LLM 하이브리드)을
> 이 Spring 리포에 직접 만드는 것으로 설계했었다. `freepets-docs/docs/02-api-design.md`(원 설계 문서)를
> 확인한 결과 **틀린 전제였다** — 정정한다.

## 실제 아키텍처 (freepets-docs/docs/02-api-design.md 기준)

```
[프론트] --판별 요청--> [Node.js 백엔드 B, :3001] --Claude API 호출--> 판별 수행
                              │
                              │ POST /internal/pet-checks (X-Internal-Key)
                              ▼
                     [Spring 백엔드 A, :8080] --DB 저장--> pet_checks / pet_check_verdicts
```

- **`POST /api/v1/ai/check`, `POST /api/v1/ai/checklist`, `POST /api/v1/ai/course-check`는 전부 별도 Node.js 저장소(백엔드 B) 소관.** Claude API를 직접 호출해 판별하는 로직도 그쪽에 있다. **이 Spring 리포에는 만들지 않는다.**
- Spring A(이 리포)가 할 일은 딱 두 가지:
  1. **`POST /internal/pet-checks`** — Node B가 판별을 끝낸 뒤 결과를 저장하는 내부 전용 엔드포인트. `X-Internal-Key` 헤더로 보호, 외부 노출 금지.
  2. **`GET /api/v1/pet-checks`** — 로그인 사용자의 판별 이력 조회(최신순).
- 관광공사 데이터 동기화 스케줄러(`@Scheduled`, 매일 03:00) 이후, `pet_condition_raw`가 갱신된 시설을 Node B의 `/internal/ai/parse-condition`(내부 전용, Claude로 `maxWeight`/`requirements` 구조화 추출)에 배치 요청하는 것도 Spring A 쪽 책임 — 이건 만든다.

## 데이터 모델 — 그룹 판별

한 번의 판별 = **여러 마리 그룹**. `overall`·`checklist`·`tips`는 그룹 공통(세션 1행), 아이별 `result`·`reason`·`conditions`는 `pet_check_verdicts`에 1:N로 저장한다.

| 테이블 | 컬럼 | 비고 |
|---|---|---|
| `pet_checks` | `check_id`(PK), `user_id`, `facility_id`, `overall`, `checklist`(JSON), `tips`(JSON), `model` | `model`은 Node B가 판별에 쓴 Claude 모델명 기록 |
| `pet_check_verdicts` | `verdict_id`(PK), `check_id`(FK), `pet_id`, `result`, `reason`, `conditions`(JSON) | |

> 현재 코드의 `PetCheck` 엔티티는 이 두 테이블을 하나로 합친 형태(펫 1마리=1행)로 되어 있다 — **그룹 판별을 지원하려면 `PetCheckVerdict` 자식 엔티티 분리가 필요.** 아래 "확인 필요" 참고.

## `POST /internal/pet-checks` (Spring A, 내부 전용)

**Request** (Node B → Spring A)
```json
{
  "userId": 1, "facilityId": 7,
  "overall": "CONDITIONAL",
  "checklist": ["리드줄 필수 지참", "휴대용 물그릇 준비"],
  "tips": ["여름 아스팔트 주의"],
  "model": "claude-...",
  "verdicts": [
    { "petId": 1, "result": "CONDITIONAL", "reason": "...", "conditions": ["리드줄 필수 착용"] },
    { "petId": 2, "result": "CONDITIONAL", "reason": "...", "conditions": ["리드줄 필수 착용"] }
  ]
}
```
**Response**: 저장된 `checkId` 포함 201.

인증: `X-Internal-Key` 헤더 검증 (JWT 아님 — 서버 간 호출).

## `GET /api/v1/pet-checks`

- 인증 필요(JWT). 내 판별 이력 최신순, 각 건에 `overall` + `verdicts[]`(아이별) 포함.
- 리뷰 작성 자격 검사(`POST /api/v1/reviews`)는 `pet_checks(user_id, facility_id)` 존재 여부로 판단 — 그룹 판별이라 시설 단위.

**Response**
```json
{
  "checkId": 5012, "facilityId": 7, "overall": "CONDITIONAL",
  "checklist": ["리드줄 필수 지참", "휴대용 물그릇 준비"], "tips": ["여름 아스팔트 주의"],
  "verdicts": [
    { "petId": 1, "result": "CONDITIONAL", "reason": "…", "conditions": ["리드줄 필수 착용"] },
    { "petId": 2, "result": "CONDITIONAL", "reason": "…", "conditions": ["리드줄 필수 착용"] }
  ]
}
```

## ⚠️ 확인/정리 필요 (코드와 문서 간 불일치)

1. **`PetCheckResult` enum 값 불일치**: 코드(`com.freepets.domain.petcheck.entity.PetCheckResult`)는 `ALLOWED, CONDITIONAL, NOT_ALLOWED, UNKNOWN`인데, `freepets-docs`의 모든 예시는 `ALLOWED, CONDITIONAL, DENIED` 3종만 쓴다(`UNKNOWN` 없음). 팀과 확인해서 한쪽에 맞춰야 함 — 이 문서는 일단 코드 쪽(`NOT_ALLOWED`/`UNKNOWN` 포함)을 유지하되, 실제 Node B 응답과 다르면 저장 시 매핑이 깨진다.
2. **`PetCheck` 엔티티가 그룹(1:N)을 지원 안 함**: 현재 필드가 `pet`(단수), `user`, `facility` 등 펫 1마리 기준이라, `/internal/pet-checks`가 여러 펫 결과를 받으면 그대로 저장할 수 없다. `PetCheckVerdict` 자식 엔티티로 분리하는 마이그레이션이 선행돼야 함.
3. **`X-Internal-Key` 검증 로직**: 아직 Security 설정에 없음 — `SecurityConfig`에 `/internal/**` 경로용 필터/인터셉터 추가 필요 (JWT 필터와 별도).
4. `Facility.contentId` ↔ 스케줄러의 `content_id` 기준 upsert — 관광공사 동기화 스케줄러(`@Scheduled`) 자체도 아직 없음. `POST /api/v1/admin/sync/tour-api`(수동 트리거) 포함해서 별도 구현 필요.
