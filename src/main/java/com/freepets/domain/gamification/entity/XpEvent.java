package com.freepets.domain.gamification.entity;

import com.freepets.domain.user.entity.User;
import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 경험치 지급 이력(append-only 원장). 감사 추적뿐 아니라 두 가지 규칙을 여기서 직접 센다 —
// (1) 도메인별 하루 지급 상한, (2) sourceId가 있는 지급의 평생 1회 여부(코스 공개 등). 두 규칙
// 모두 GamificationService가 이 테이블을 조회해서 판단하므로, 행 자체엔 그 이상의 상태를 담지
// 않는다(수정될 일이 없는 로그라 BaseEntity의 updatedAt은 쓰이지 않지만, 이 리포 대부분의
// 엔티티가 BaseEntity를 상속하는 관례를 따른다).
@Getter
@Entity
@Table(
        name = "xp_events",
        indexes = {
                // 하루 상한 조회(user_id + source_type + createdAt 범위)가 이 인덱스를 탄다.
                @Index(name = "idx_xp_events_user_source_created", columnList = "user_id, source_type, created_at"),
                // 오늘의 퀘스트 조회(GamificationQueryService.getTodayQuests)는 source_type 없이
                // user_id + createdAt 범위만으로 전체 소스타입을 한 번에 그룹핑한다 — 위 인덱스는
                // source_type이 중간에 끼어있어 source_type 조건이 없으면 createdAt 범위를 못
                // 타므로, 그 유저의 XpEvent 전체를 훑게 된다. 그래서 이 조회 전용으로 따로 둔다.
                @Index(name = "idx_xp_events_user_created", columnList = "user_id, created_at")
        },
        uniqueConstraints = {
                // GamificationService가 저장 전에 existsBy...로 중복 지급을 걸러내지만, 그 확인과
                // 저장 사이에는 여전히 레이스가 남는다(동시에 두 요청이 같은 확인을 통과해버릴 수
                // 있음). sourceId가 있는 지급은 항상 그 값이 유일하게 발급되는 값(리뷰 id, 코스
                // id 등)이라 정상 흐름에서는 절대 겹치지 않으므로, DB 제약으로 마지막 방어선을
                // 둔다 — UserBadge의 uk_user_badges_user_badge와 같은 목적.
                @UniqueConstraint(name = "uk_xp_events_user_source", columnNames = {"user_id", "source_type", "source_id"}),
                // componentSignature가 있는 지급(코스 공개 등, 스톱 구성 단위 중복 방지)의 DB
                // 레벨 마지막 방어선 — 위와 같은 이유. component_signature는 대부분의 sourceType엔
                // null이고(예: 판별·리뷰), null끼리는 유니크 제약에서 서로 겹친 것으로 안 치므로
                // 이 컬럼을 안 쓰는 sourceType들끼리는 서로 아무 영향이 없다.
                @UniqueConstraint(
                        name = "uk_xp_events_user_source_type_component_signature",
                        columnNames = {"user_id", "source_type", "component_signature"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class XpEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "xp_event_id")
    private Long xpEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private XpSourceType sourceType;

    // 이 지급을 유발한 행의 id(판별 id, 리뷰 id, 코스 id 등) — 모든 호출부가 항상 값을 넘긴다.
    // 매번 새로 생성되는 행의 id라 같은 유형이 하루에 여러 번 지급돼도(판별 등) 자연히
    // sourceId가 매번 달라 평생 1회 검사·유니크 제약과 부딪히지 않는다.
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private int amount;

    // 이 지급이 어떤 구성요소들(코스 공개의 스톱 시설 id 등)에 대한 것인지를 정렬된 id 목록
    // 문자열로 담는다(예: "1,2,3") — sourceId(courseId)는 코스를 새로 만들 때마다 값이 바뀌어서
    // "같은 구성요소 조합으로 이미 지급받았는지"를 못 걸러내는데, 이 값은 구성요소 자체가 바뀌지
    // 않는 한 그대로라 스톱 구성(순서 무관) 기준 평생 1회 판정에 쓸 수 있다. Course 엔티티를 다시
    // 조회하지 않아도 되도록 지급 시점 값을 그대로 굳혀서(스냅샷) 저장한다 — 이후 그 코스가
    // 수정되거나 삭제돼도 이 값은 안 바뀐다. componentId 개념이 없는 대부분의 sourceType(판별·
    // 리뷰 등)은 null을 그대로 둔다.
    @Column(name = "component_signature", length = 300)
    private String componentSignature;

    @Builder
    private XpEvent(
            User user,
            XpSourceType sourceType,
            Long sourceId,
            int amount,
            String componentSignature
    ) {
        this.user = user;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.amount = amount;
        this.componentSignature = componentSignature;
    }

}
