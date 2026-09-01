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
