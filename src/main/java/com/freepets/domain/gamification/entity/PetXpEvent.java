package com.freepets.domain.gamification.entity;

import com.freepets.domain.pet.entity.Pet;
import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 반려동물 개별 경험치 지급 이력. XpEvent(User 단위)와 같은 목적의 append-only 원장이지만
// 완전히 별도 테이블이다 — XpEvent의 평생 1회 유니크 제약은 (user_id, source_type, source_id)라,
// 판별처럼 한 번의 지급(sourceId 하나)이 여러 반려동물에게 동시에 나가면 그 제약에 바로 걸린다.
// 이 테이블은 유니크 축을 (pet_id, source_type, source_id)로 따로 둬서 "같은 sourceId, 서로 다른
// pet_id마다 한 행"이 자연스럽게 맞아떨어지게 한다. 지급 자체의 판정(하루 상한·평생 1회)은
// 여전히 XpEvent/User 쪽에서만 하고, 이 테이블은 그 판정을 통과한 지급을 반려동물별로 한 번 더
// 기록만 한다 — GamificationService.creditPets 참고.
@Getter
@Entity
@Table(
        name = "pet_xp_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_pet_xp_events_pet_source", columnNames = {"pet_id", "source_type", "source_id"}),
                @UniqueConstraint(
                        name = "uk_pet_xp_events_pet_source_type_component_signature",
                        columnNames = {"pet_id", "source_type", "component_signature"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetXpEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pet_xp_event_id")
    private Long petXpEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private XpSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private int amount;

    // XpEvent.componentSignature와 같은 값을 그대로 들고 온다 — 의미·용도는 동일(호출부 없는
    // 대부분의 sourceType은 null).
    @Column(name = "component_signature", length = 300)
    private String componentSignature;

    @Builder
    private PetXpEvent(
            Pet pet,
            XpSourceType sourceType,
            Long sourceId,
            int amount,
            String componentSignature
    ) {
        this.pet = pet;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.amount = amount;
        this.componentSignature = componentSignature;
    }

}
