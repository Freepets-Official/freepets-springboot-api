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
