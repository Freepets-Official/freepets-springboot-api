# 08. 코스 판별 결과 + 대체 제안 (F3) — Node B 소관, 이 리포엔 구현 없음

> **[정정]** 이 문서의 이전 버전은 `POST /api/v1/ai/course-check`를 Spring 리포에 구현하는 것으로
> 설계했었다. `freepets-docs/docs/02-api-design.md` 확인 결과 **이 엔드포인트는 Node.js 백엔드 B(`:3001`)
> 소관**이다. Spring A(이 리포)는 이 기능을 위해 **아무것도 새로 구현하지 않는다** — Node B가 이미 존재하는
> Spring A의 조회 API(코스 조회, 시설 조회, 펫 조회)를 그대로 소비할 뿐이다.

## 왜 Node B인가

- 코스 판별은 각 스톱마다 06의 "낱개 판별"과 완전히 같은 규칙을 재사용해야 한다 — 그 판별 로직(Claude 호출) 자체가 Node B에 있으므로, 판별을 여러 번 반복 호출하는 코스 판별도 자연히 Node B에 있다.
- Spring A가 이걸 따로 구현하면 판별 규칙이 두 군데(Spring 규칙 엔진 vs Node B Claude 호출)로 갈라져 "낱개와 코스가 다른 답을 내면 안 된다"는 원칙이 코드 구조상 깨진다.

## Spring A가 이미 제공해야 하는 것 (Node B가 소비)

Node B가 `POST /api/v1/ai/course-check`를 처리하려면 아래를 Spring A에 물어본다 — **전부 07/06 문서에 이미 있는 기존 API로 충족됨, 추가 구현 불필요**:

| Node B가 필요로 하는 정보 | Spring A 제공 API |
|---|---|
| 코스의 스톱 목록(순서 포함) | `GET /api/v1/courses/{courseId}` 또는 요청 Body의 `stopFacilityIds` 그대로 사용 (프론트가 직접 넘김) |
| 스톱별 시설 상세(조건 원문, `maxWeight`, `requirements`) | `GET /api/v1/facilities/{facilityId}` |
| 펫 정보(체중·품종 등) | `GET /api/v1/pets/{petId}` |
| 대체 시설 후보(같은 카테고리, 주변) | `GET /api/v1/facilities/nearby` |
| 판별 결과 저장 | `POST /internal/pet-checks` (06 문서) — 코스 판별도 스톱마다 이걸 호출해 이력에 남길지는 Node B/팀 결정 사항 |

## 참고용 — Node B 쪽 계약 (freepets-docs/docs/02-api-design.md 발췌, Spring A 구현과 무관)

`POST /api/v1/ai/course-check` Request/Response 형태만 알아두면 됨(Spring A가 만들 것은 없음):

```json
// Request
{ "petIds": [3, 4], "stopFacilityIds": [1, 2, 5, 4] }

// Response
{
  "overall": "DENIED",
  "blockedCount": 1,
  "stops": [
    {
      "facilityId": 2, "time": "11:30", "result": "DENIED",
      "verdicts": [
        { "petId": 3, "result": "ALLOWED", "reason": "...", "conditions": [] },
        { "petId": 4, "result": "DENIED", "reason": "보리(27.5kg)는 최대 허용 체중 10kg을 초과해서 입장이 어려워요.", "conditions": [] }
      ],
      "alternative": { "facilityId": 7, "name": "헤이도그 애견호텔&카페", "category": "CAFE", "distanceM": 1900 }
    }
  ]
}
```

대체 시설 선정 규칙: 같은 `category` · 그룹 전체가 갈 수 있음 · 이미 코스에 없음 · 조건부보다 완전 가능 우선 · 그다음 거리순. 없으면 `alternative: null`. 시간대: 첫 스톱 10:00, 스톱당 +90분(데모 고정).

## 이 문서가 남아있는 이유

Spring A 개발자가 "왜 course-check API가 이 리포에 없지?"라는 의문을 갖지 않도록, 그리고 Node B 담당자와 인터페이스 맞출 때(대체 시설 후보를 `nearby` API로 줄지, 전용 API를 새로 팔지 등) 참고하기 위해 남겨둔다. **구현 착수 항목 아님.**
