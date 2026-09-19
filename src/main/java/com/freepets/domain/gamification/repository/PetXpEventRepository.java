package com.freepets.domain.gamification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.gamification.entity.PetXpEvent;

public interface PetXpEventRepository extends JpaRepository<PetXpEvent, Long> {

    // 중복(uk_pet_xp_events_pet_source, uk_pet_xp_events_pet_source_type_component_signature)을
    // 예외가 아니라 삽입 자체를 무시하는 것으로 처리한다 — Postgres는 트랜잭션 안에서 statement
    // 하나가 제약 위반으로 실패하면 그 트랜잭션 전체를 abort 상태로 만들어서(SQLSTATE 25P02),
    // GamificationService.creditPets처럼 여러 반려동물을 한 트랜잭션 안에서 순회하며 저장할 때
    // save()를 던지고 잡는 방식으로는 다음 반려동물의 정상 저장까지 막힌다(PetXpEventWriter의
    // 예전 NESTED 전파 시도가 겪은 문제와 같은 근본 원인). ON CONFLICT DO NOTHING은 Postgres
    // 입장에서 아예 에러가 아니라서, 별도 트랜잭션(REQUIRES_NEW)이나 세이브포인트 없이 같은
    // 트랜잭션 안에서 안전하게 반복 호출할 수 있다. 반환값(영향받은 행 수)이 0이면 중복이라
    // 무시됐다는 뜻이다.
    @Modifying
    @Query(
            value = """
                    INSERT INTO freepets.pet_xp_events
                        (pet_id, source_type, source_id, amount, component_signature, created_at, updated_at)
                    VALUES (:petId, :sourceType, :sourceId, :amount, :componentSignature, now(), now())
                    ON CONFLICT DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIgnoringDuplicate(
            @Param("petId") Long petId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") Long sourceId,
            @Param("amount") int amount,
            @Param("componentSignature") String componentSignature
    );
}
