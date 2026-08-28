package com.freepets.domain.petcheck.entity;

import java.util.ArrayList;
import java.util.List;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.user.entity.User;
import com.freepets.global.entity.BaseEntity;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 판별 세션(그룹) — 한 번에 여러 마리를 판별한다. overall·checklist·tips는 그룹 공통이고,
// 아이별 결과는 PetCheckVerdict(1:N)에 담는다. db/schema.sql "4. pet_checks" 참고.
@Getter
@Entity
@Table(name = "pet_checks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetCheck extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "check_id")
    private Long checkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    // 그룹 종합 결과 — 아이들 result 중 최댓값(하나라도 DENIED면 DENIED)
    // 라이브 DB에 이미 pet_checks 행이 쌓여있어 컬럼 추가 시 기본값이 필요하다(NOT_PROCESSED와 같은 이유).
    @ColumnDefault("'ALLOWED'")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PetCheckResult overall;

    // 방문 준비 체크리스트(그룹 공통). ["리드줄 필수 지참", ...]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String checklist;

    // 계절·상황 팁(그룹 공통). ["한낮 아스팔트는...", ...]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String tips;

    // 판별이 규칙 엔진이면 NULL. 추후 Claude 재판별 경로가 생기면 그때 채운다.
    @Column(length = 50)
    private String model;

    // 이력 목록 조회가 판별 세션마다 아이별 결과를 함께 내려주므로, 지연 로딩만 두면 N+1이 난다 —
    // Facility.checkLists와 같은 이유로 batch fetch를 건다.
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "petCheck", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PetCheckVerdict> verdicts = new ArrayList<>();

    @Builder
    private PetCheck(
            User user,
            Facility facility,
            PetCheckResult overall,
            String checklist,
            String tips,
            String model
    ) {
        this.user = user;
        this.facility = facility;
        this.overall = overall;
        this.checklist = checklist;
        this.tips = tips;
        this.model = model;
    }

    public void addVerdict(PetCheckVerdict verdict) {
        verdicts.add(verdict);
        verdict.assignPetCheck(this);
    }

}
