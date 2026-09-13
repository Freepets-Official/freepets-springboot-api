package com.freepets.domain.course.entity;

import java.util.ArrayList;
import java.util.List;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.user.entity.User;
import com.freepets.global.entity.BaseEntity;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;

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

// 여행 코스. source에 따라 성격이 다르다 — CUSTOM은 사용자 소유물, PRESET은 지역×테마별로 하나씩
// 서버가 관리하는 캐시(user는 null), RECOMMENDED(우리 아이 취향/취향 비슷한 새곳)는 개인화 결과라
// 애초에 저장하지 않는다(요청마다 재계산, 이 엔티티로 만들어지지 않음).
@Getter
@Entity
@Table(name = "courses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_id")
    private Long courseId;

    // PRESET은 운영자/배치가 만드는 공용 코스라 소유자가 없다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseSource source;

    // PRESET 캐시의 지역 축. Facility.sido/sigungu와 동일한 값으로 비교한다(자유텍스트 아님).
    // 나이틀리 재계산 배치가 다시 조회할 때 하나의 문자열(예: "강원 강릉시")을 sido/sigungu로
    // 역분해하면 지명에 공백이 섞인 경우 깨질 수 있어(예: "경기 수원시 영통구") 처음부터 컬럼을
    // 나눈다. sigungu는 시/도 전체 대상일 때 null.
    @Column(length = 20)
    private String sido;

    @Column(length = 20)
    private String sigungu;

    // PRESET 캐시의 테마 축. CUSTOM은 항상 null.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CourseTheme theme;

    // PRESET 캐시의 거리 축. theme과 같은 이유로 CUSTOM은 항상 null — 연속값이 아니라
    // CourseDistanceOption 고정 구간만 허용해야 지역×테마×거리 조합이 늘어도 캐시가 유효하다.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CourseDistanceOption distanceOption;

    // 목록 조회에서 스톱을 같이 내려줄 때 N+1을 막는다 — Facility.checkLists와 같은 이유.
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseStop> stops = new ArrayList<>();

    // CUSTOM 코스를 다른 사용자에게도 "둘러보기" 목록에 공개할지. CUSTOM만 의미 있는 값이고
    // (트리플의 "다른 여행자 코스"처럼), PRESET은 이미 공용이라 이 값과 무관하게 항상 조회 가능,
    // RECOMMENDED는 애초에 이 테이블에 저장되지 않아 해당 없음. 기본값 false(비공개).
    // Facility.isDangerousBreedExcluded와 같은 이유로 @ColumnDefault 필요 — 라이브 DB엔 이미
    // courses가 있어(PRESET 캐시), 기본값 없이 컬럼만 추가하면 NOT NULL 위반으로 마이그레이션이 실패한다.
    @ColumnDefault("false")
    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    // 코스 공유 코드(CourseShareCodeGenerator로 발급). 코드를 아는 사람이면 누구나 이 코스를 자기
    // 코스로 복사해갈 수 있다 — isPublic과 무관하게 동작하는 별도의 1:1 공유 경로다
    // (PetCheckVerdict.verifyCode와 같은 논리). 발급 전엔 null, 발급은 1회만(재발급 시 기존 값 유지).
    // 기존 라이브 코스엔 채울 값이 없어 nullable로 두고 백필하지 않는다.
    @Column(name = "share_code", unique = true, length = 20)
    private String shareCode;

    // 공유 코드로 이 코스가 복사된 누적 횟수(GET /courses/public 인기순 폴백의 신호). isPublic과
    // 무관하게 셈한다 — 비공개 코스도 지인에게 공유 코드로만 알려주는 쓰임이 있고, 이후 공개로
    // 전환됐을 때 그동안의 인기를 반영해야 하기 때문. 기존 라이브 코스는 전부 0부터 시작한다.
    @ColumnDefault("0")
    @Column(name = "copy_count", nullable = false)
    private int copyCount;

    @Builder
    private Course(
            User user,
            String name,
            String description,
            CourseSource source,
            String sido,
            String sigungu,
            CourseTheme theme,
            CourseDistanceOption distanceOption,
            boolean isPublic
    ) {
        this.user = user;
        this.name = name;
        this.description = description;
        this.source = source;
        this.sido = sido;
        this.sigungu = sigungu;
        this.theme = theme;
        this.distanceOption = distanceOption;
        this.isPublic = isPublic;
    }

    /**
     * CUSTOM 코스 수정, PRESET 나이틀리 재계산 둘 다 쓴다 — 매번 새 행을 만들지 않고 기존 행을
     * 갱신한다(PRESET은 지역×테마 조합당 행 1개를 계속 재사용).
     */
    public void update(
            String name,
            String description,
            List<Facility> stopFacilitiesInOrder
    ) {
        this.name = name;
        this.description = description;
        replaceStops(stopFacilitiesInOrder);
    }

    public void replaceStops(List<Facility> stopFacilitiesInOrder) {
        this.stops.clear();
        int order = 0;
        for (Facility facility : stopFacilitiesInOrder) {
            this.stops.add(CourseStop.builder()
                    .course(this)
                    .facility(facility)
                    .stopOrder(order++)
                    .build());
        }
    }

    public boolean isOwnedBy(Long userId) {
        return user != null && user.getId().equals(userId);
    }

    /**
     * 공개 여부만 바꾼다. update()에 넣지 않고 분리한 이유는 update()를 PRESET 나이틀리
     * 재계산(CoursePresetService)도 같이 쓰는데, PRESET엔 공개 여부 개념이 없어서다.
     */
    public void updateVisibility(boolean isPublic) {
        this.isPublic = isPublic;
    }

    /**
     * 공유 코드를 발급한다. idempotent하게 쓰라고 두는 메서드라 이미 값이 있으면 덮어쓰지 않는다 —
     * 실제 "이미 있으면 새로 만들지 않는다" 판단은 호출부(CourseCommandService)가 shareCode가
     * null인지 먼저 확인하고서만 이 메서드를 부르는 방식으로 한다.
     */
    public void issueShareCode(String shareCode) {
        this.shareCode = shareCode;
    }

    public void incrementCopyCount() {
        this.copyCount++;
    }

}
