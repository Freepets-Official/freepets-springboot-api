package com.freepets.domain.course.entity;

import java.util.ArrayList;
import java.util.List;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.user.entity.User;
import com.freepets.global.entity.BaseEntity;

import org.hibernate.annotations.BatchSize;

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

    // 목록 조회에서 스톱을 같이 내려줄 때 N+1을 막는다 — Facility.checkLists와 같은 이유.
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseStop> stops = new ArrayList<>();

    @Builder
    private Course(
            User user,
            String name,
            String description,
            CourseSource source,
            String sido,
            String sigungu,
            CourseTheme theme
    ) {
        this.user = user;
        this.name = name;
        this.description = description;
        this.source = source;
        this.sido = sido;
        this.sigungu = sigungu;
        this.theme = theme;
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

}
