package com.freepets.domain.gamification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.gamification.entity.PetXpEvent;
import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.gamification.repository.PetXpEventRepository;
import com.freepets.domain.pet.entity.Pet;

import lombok.RequiredArgsConstructor;

/**
 * 반려동물 개별 경험치 저장을 별도 세이브포인트(NESTED)로 감싼다.
 *
 * <p>GamificationService.creditPets는 여러 반려동물을 순회하며 저장하는데, Postgres는 트랜잭션
 * 안에서 statement 하나가 제약 위반으로 실패하면 그 트랜잭션 전체를 abort 상태로 만든다
 * (SQLSTATE 25P02) — 이후 같은 트랜잭션의 모든 statement가 원인과 무관하게 막힌다. 반려동물
 * 한 마리의 중복 저장 실패가 나머지 반려동물의 정상 저장이나 User XP 지급 커밋까지 막으면
 * 안 되므로, 반려동물마다 별도 세이브포인트를 잡아 실패하면 그 반려동물만 롤백하고 나머지는
 * 계속 진행한다.
 *
 * <p>NESTED 전파는 같은 클래스 안에서 private 메서드를 호출하는 방식으로는 Spring 프록시를
 * 타지 않는다(self-invocation) — GamificationNotificationService가 @Async를 위해 같은
 * 이유로 별도 빈으로 분리해둔 패턴을 그대로 따른다.
 */
@Service
@RequiredArgsConstructor
public class PetXpEventWriter {

    private final PetXpEventRepository petXpEventRepository;

    @Transactional(propagation = Propagation.NESTED)
    public void save(
            Pet pet,
            XpSourceType sourceType,
            Long sourceId,
            int amount,
            String componentSignature
    ) {
        petXpEventRepository.save(
                PetXpEvent.builder()
                        .pet(pet)
                        .sourceType(sourceType)
                        .sourceId(sourceId)
                        .amount(amount)
                        .componentSignature(componentSignature)
                        .build()
        );
    }
}
