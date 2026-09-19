package com.freepets.domain.gamification.service;

import org.springframework.stereotype.Service;

import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.gamification.repository.PetXpEventRepository;
import com.freepets.domain.pet.entity.Pet;

import lombok.RequiredArgsConstructor;

/**
 * 반려동물 개별 경험치 저장 — 중복(uk_pet_xp_events_pet_source,
 * uk_pet_xp_events_pet_source_type_component_signature)을 예외가 아니라 삽입 자체를 무시하는
 * 것으로 처리한다.
 *
 * <p>GamificationService.creditPets는 여러 반려동물을 같은 트랜잭션 안에서 순회하며 저장하는데,
 * Postgres는 트랜잭션 안에서 statement 하나가 제약 위반으로 실패하면 그 트랜잭션 전체를 abort
 * 상태로 만든다(SQLSTATE 25P02) — 이후 같은 트랜잭션의 모든 statement가 원인과 무관하게 막힌다.
 * 예전엔 이걸 세이브포인트(NESTED)나 별도 트랜잭션(REQUIRES_NEW)으로 우회하려 했지만, 전자는
 * 이 앱의 기본 JpaTransactionManager가 nestedTransactionAllowed를 안 켜서 자체가 실패했고,
 * 후자는 동작은 하지만 바깥 트랜잭션이 나중에 롤백돼도 이미 커밋된 반려동물 XP 행이 같이
 * 롤백되지 않는 트레이드오프가 있었다. INSERT ... ON CONFLICT DO NOTHING은 Postgres 입장에서
 * 애초에 에러가 아니라서, 같은 트랜잭션 안에서 반려동물마다 반복 호출해도 트랜잭션이 abort되지
 * 않고, 바깥 트랜잭션과 커밋·롤백 운명을 그대로 같이한다(PetXpEventRepositoryTest 참고).
 */
@Service
@RequiredArgsConstructor
public class PetXpEventWriter {

    private final PetXpEventRepository petXpEventRepository;

    /**
     * @return 실제로 삽입됐으면(=중복이 아니었으면) true, 이미 지급된 조합이라 무시됐으면 false.
     */
    public boolean save(
            Pet pet,
            XpSourceType sourceType,
            Long sourceId,
            int amount,
            String componentSignature
    ) {
        int insertedRows = petXpEventRepository.insertIgnoringDuplicate(
                pet.getPetId(), sourceType.name(), sourceId, amount, componentSignature
        );
        return insertedRows > 0;
    }
}
