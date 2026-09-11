-- 이 리포에는 Flyway/Liquibase 같은 마이그레이션 도구가 없고, JPA ddl-auto=update가 컬럼
-- 추가/생성만 자동으로 해준다. DROP/ALTER 같이 자동으로 안 되는 변경은 여기에 적어두고
-- DB 권한 있는 사람이 직접 실행한다. 실행한 항목은 지우지 말고 "적용 완료" 표시만 남긴다.

-- ============================================================
-- 2026-08-28 — PetCheck 그룹 판별 구조 전환 (#30, feat/#30-ai-check-condition-parsing)
-- ============================================================
-- PetCheck이 반려동물 1마리당 1행(pet_id NOT NULL) 구조에서 그룹 판별 세션 구조로
-- 바뀌면서, 엔티티가 더 이상 pet_checks.pet_id에 값을 채우지 않는다(아이별 결과는
-- 새 테이블 pet_check_verdicts.pet_id로 이동). ddl-auto=update는 컬럼을 자동으로
-- 드랍/완화하지 않으므로 수동 조치가 필요하다.
--
-- 상태: ✅ 적용 완료 (2026-08-28, 개발 DB)
ALTER TABLE pet_checks ALTER COLUMN pet_id DROP NOT NULL;

-- 위 조치는 임시 완화(컬럼은 남아있음)다. 그룹 판별 구조가 안정화되고 모두 확인되면
-- 컬럼 자체를 정리하는 게 맞다 — 실행 전 팀 확인 필요.
-- 상태: ⬜ 미적용 (팀 논의 후 진행)
-- ALTER TABLE pet_checks DROP COLUMN pet_id;

-- ============================================================
-- 2026-09-01 — 발자국 랭킹 집계 캐시 (#45, feat/#45-facility-ranking-api)
-- ============================================================
-- 랭킹은 전체 시설을 친화도 점수순으로 정렬해야 해서, 조회 시점 집계로는 매 요청마다 리뷰
-- 전체를 group by 하게 된다. 그래서 점수·리뷰 수·등급을 시설에 저장해두기로 했다.
--
-- review_count / paw_grade_level 컬럼과 idx_facilities_paw_grade_ranking 인덱스는
-- ddl-auto=update가 자동으로 만든다. 타입 변경은 자동으로 안 되므로 아래만 수동 조치가 필요하다.
--
-- pet_score를 정수에서 실수로 바꾼다. 등급 판정은 반올림 전 원점수로 해야 하는데,
-- 87.96을 88로 저장하면 88점이 기준인 4등급으로 잘못 올라간다.
-- 현재 이 컬럼은 값을 채우는 코드가 없어 전 행이 null이라 안전하다.
--
-- 상태: ⬜ 미적용
ALTER TABLE facilities ALTER COLUMN pet_score TYPE double precision;

-- ddl-auto가 정렬 방향까지 반영하지 못해 인덱스가 안 생겼다면 아래를 직접 실행한다.
-- 랭킹의 정렬 순서와 같아야 인덱스만 읽고 페이징이 끝난다.
-- 상태: ⬜ 미적용 (자동 생성되면 실행 불필요)
-- CREATE INDEX IF NOT EXISTS idx_facilities_paw_grade_ranking
--     ON facilities (paw_grade_level DESC, pet_score DESC, facility_id);

-- 위 ALTER 뒤에 기존 리뷰를 시설 캐시에 1회 반영해야 한다.
--   ./gradlew facilityGradeBackfill

-- ============================================================
-- 2026-09-03 — 소셜 로그인 도입 (카카오/네이버/구글/애플)
-- ============================================================
-- 소셜 가입자는 비밀번호가 없어 users.password_hash가 null이 된다. ddl-auto=update는
-- NOT NULL을 자동으로 완화하지 않으므로 수동 조치가 필요하다.
-- users.provider_id 컬럼과 (provider, provider_id) 유니크 제약은 ddl-auto=update가 만들어 준다.
-- 기존 LOCAL 유저 데이터는 영향이 없다(값이 이미 채워져 있고 제약만 느슨해진다).
--
-- 상태: ⬜ 미적용
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- ============================================================
-- 2026-09-07 — 거부 제보(F4) denial_reason CHECK 제약조건 갱신 (#53)
-- ============================================================
-- facility_reports.denial_reason에 옛날 값(제보 자체가 반려된 사유였던 시절)만 허용하는
-- CHECK 제약조건이 남아있어서, F4에서 바뀐 새 DenialReason 값(WEIGHT 등)을 저장하려 하면
-- 전부 ConstraintViolationException → COMMON500으로 떨어진다. ddl-auto=update는 CHECK
-- 제약조건을 자동으로 안 바꾸므로 수동 조치가 필요하다.
-- (프론트 연동 중 발견 — POST /facilities/{id}/denial-reports가 항상 500이었던 원인)
--
-- 기존 제약조건 실제 정의(SQL Editor로 확인):
--   CHECK ((denial_reason)::text = ANY (ARRAY['DUPLICATE','INSUFFICIENT_EVIDENCE',
--                                              'NOT_VERIFIABLE','IRRELEVANT_CONTENT']))
--
-- 상태: ✅ 적용 완료 (2026-09-07, 운영 DB) — 단, 아래 report_type/status 건이 뒤이어 발견됨(같은
-- INSERT가 여러 컬럼에 걸쳐 옛날 CHECK 제약조건에 막혀있었다. 이건 그 중 첫 번째 층이었을 뿐).
-- 테이블이 public이 아니라 freepets 스키마에 있다 — 스키마 안 붙이면 "relation does not exist"로 실패한다.
ALTER TABLE freepets.facility_reports DROP CONSTRAINT facility_reports_denial_reason_check;
ALTER TABLE freepets.facility_reports ADD CONSTRAINT facility_reports_denial_reason_check
    CHECK (denial_reason IN ('WEIGHT', 'BREED', 'INDOOR', 'POLICY_CHANGED', 'CROWDED', 'OTHER'));

-- ============================================================
-- 2026-09-08 — 거부 제보(F4) report_type·status CHECK 제약조건 갱신 (#53)
-- ============================================================
-- 위 denial_reason을 고친 뒤 다시 테스트하니 같은 INSERT가 이번엔 report_type_check에서
-- 막혔다 — Postgres는 위반된 첫 제약조건에서 바로 실패하고 멈추기 때문에, 어제는 denial_reason
-- 뒤에 report_type·status도 옛날 값만 허용하고 있다는 게 가려져 있었다.
--
-- ReportType.DENIED, ReportStatus.APPLIED는 F4 구현 때 기존 값 유지한 채 추가된 새 값인데
-- (git log 확인: e6416385), report_type_check·status_check 둘 다 여전히 그 이전 값만 허용한다.
-- 즉 지금까지 거부 제보는 한 번도 실제로 저장에 성공한 적이 없다 — denial_reason만 고쳐서는
-- 부족하고, 이 두 제약조건도 같이 갱신해야 완전히 해결된다.
--
-- 상태: ⬜ 미적용 (긴급 — 이거 때문에 거부 제보 저장이 여전히 전부 실패 중)
ALTER TABLE freepets.facility_reports DROP CONSTRAINT facility_reports_report_type_check;
ALTER TABLE freepets.facility_reports ADD CONSTRAINT facility_reports_report_type_check
    CHECK (report_type IN ('INFO_CORRECTION', 'PET_POLICY_CHANGE', 'PERMANENTLY_CLOSED', 'NEW_FACILITY', 'ETC', 'DENIED'));

ALTER TABLE freepets.facility_reports DROP CONSTRAINT facility_reports_status_check;
ALTER TABLE freepets.facility_reports ADD CONSTRAINT facility_reports_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'APPLIED'));

-- ============================================================
-- 2026-09-11 — 회원 탈퇴 API 추가 (feat/withdraw-account)
-- ============================================================
-- 탈퇴 시 users.email을 null로 비워야 같은 이메일로 즉시 재가입할 수 있다(유니크 제약과
-- 안 부딪히게). 지금까지 email은 NOT NULL이었는데, ddl-auto=update는 NOT NULL을 자동으로
-- 풀어주지 않으므로 수동 조치가 필요하다. 기존 활성 유저 데이터는 영향이 없다(값이 이미
-- 채워져 있고 제약만 느슨해진다).
--
-- 상태: ⬜ 미적용
ALTER TABLE freepets.users ALTER COLUMN email DROP NOT NULL;

-- ============================================================
-- 2026-09-10 — 캘린더 일정 기간(endDate) 지원 (#68)
-- ============================================================
-- 여행처럼 며칠에 걸치는 일정을 표현하려고 calendar_events.end_date 컬럼을 추가했다.
-- ddl-auto=update가 컬럼 자체는 자동으로 만들어주지만(nullable로 선언해서 기존 행이 있어도
-- 실패하지 않는다), 기존 행의 end_date는 채워주지 않아 전부 null로 남는다 — 애플리케이션
-- 계층(CalendarEvent.getEndDate())이 null이면 start_date로 대체해서 동작 자체는 문제없지만,
-- DB에서 직접 조회하는 배치·리포팅이 있다면 null을 다르게 취급할 수 있으니 백필해둔다.
--
-- 상태: ⬜ 미적용
UPDATE calendar_events SET end_date = start_date WHERE end_date IS NULL;

-- ============================================================
-- 2026-09-12 — 코스 "거리 제한 없음" 선택 시 500 오류 (courses.distance_option CHECK 제약조건 갱신, #48)
-- ============================================================
-- CourseDistanceOption에 UNLIMITED가 나중에 추가됐는데(#48 초기 커밋 d53f663엔 없었고,
-- f4d0042에서 추가됨), courses.distance_option 컬럼은 테이블이 처음 만들어질 때 Hibernate가
-- 그 시점의 enum 값(ONE_KM/FIVE_KM/TEN_KM/TWENTY_KM/THIRTY_KM)만으로 CHECK 제약조건을 자동
-- 생성해뒀을 가능성이 높다 — denial_reason·report_type·status(바로 위 두 항목)와 완전히 같은
-- 패턴이다. ddl-auto=update는 CHECK 제약조건을 자동으로 갱신하지 않는다.
--
-- "거리 제한 없음"을 선택하면 GET /courses/preset(단일 테마 조합)이 그 값을 courses 테이블에
-- 캐시로 저장하려다 이 CHECK 제약조건을 위반해 500이 난다(리포트: "거리 제한 없음을 선택했을때
-- 서버 내 오류 발생"). liked/similar는 이 값을 DB에 저장하지 않고 메모리에서만 써서 영향이 없다.
--
-- 실행 전 SQL Editor로 기존 제약조건이 실제로 있는지, 있다면 정확한 이름·값 목록을 먼저
-- 확인할 것 — 위 사례들처럼 이름이 다를 수 있다. 아래는 Postgres 기본 명명 규칙 기준 추정이다.
--
-- 상태: ⬜ 미적용
ALTER TABLE freepets.courses DROP CONSTRAINT IF EXISTS courses_distance_option_check;
ALTER TABLE freepets.courses ADD CONSTRAINT courses_distance_option_check
    CHECK (distance_option IN ('ONE_KM', 'FIVE_KM', 'TEN_KM', 'TWENTY_KM', 'THIRTY_KM', 'UNLIMITED'));
