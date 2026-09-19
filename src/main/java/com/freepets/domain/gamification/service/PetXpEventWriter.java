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
 * 반려동물 개별 경험치 저장을 별도 트랜잭션(REQUIRES_NEW)으로 감싼다.
 *
 * <p>GamificationService.creditPets는 여러 반려동물을 순회하며 저장하는데, Postgres는 트랜잭션
 * 안에서 statement 하나가 제약 위반으로 실패하면 그 트랜잭션 전체를 abort 상태로 만든다
 * (SQLSTATE 25P02) — 이후 같은 트랜잭션의 모든 statement가 원인과 무관하게 막힌다. 반려동물
 * 한 마리의 중복 저장 실패가 나머지 반려동물의 정상 저장이나 User XP 지급 커밋까지 막으면
 * 안 되므로, 반려동물마다 독립된 트랜잭션으로 저장해 실패하면 그 반려동물만 롤백하고 나머지는
 * 계속 진행한다.
 *
 * <p>원래 세이브포인트 기반 NESTED 전파를 썼으나, 이 앱의 기본 JpaTransactionManager는
 * nestedTransactionAllowed가 꺼져 있어(spring-boot-starter-data-jpa 기본 설정, 별도로 켜는
 * 코드 없음) NESTED가 저장 시도마다 NestedTransactionNotSupportedException을 던지고 그 예외가
 * creditPets의 DataIntegrityViolationException catch에 안 잡혀 호출부 트랜잭션 전체가 롤백되는
 * 버그가 있었다. REQUIRES_NEW는 세이브포인트가 아니라 완전히 독립된 물리 트랜잭션을 새로
 * 열어서 같은 격리 목적(한 마리 실패가 나머지를 막지 않음)을 별도 설정 없이 달성한다 — 대신
 * 이 메서드가 커밋한 뒤 바깥 트랜잭션(User XP 지급·리뷰 등 원래 액션)이 나중에 다른 이유로
 * 롤백되면, 이미 커밋된 반려동물 경험치 행은 같이 롤백되지 않고 남는다. 오늘 기준 creditPets
 * 호출 이후에 바깥 트랜잭션이 실패할 경로가 없어 감수 가능한 트레이드오프로 판단했다.
 *
 * <p>REQUIRES_NEW 전파도 같은 클래스 안에서 private 메서드를 호출하는 방식으로는 Spring
 * 프록시를 타지 않는다(self-invocation) — GamificationNotificationService가 @Async를 위해 같은
 * 이유로 별도 빈으로 분리해둔 패턴을 그대로 따른다.
 */
@Service
@RequiredArgsConstructor
public class PetXpEventWriter {

    private final PetXpEventRepository petXpEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
