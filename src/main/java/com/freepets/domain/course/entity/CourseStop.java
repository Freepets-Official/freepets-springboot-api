package com.freepets.domain.course.entity;

import com.freepets.domain.facility.entity.Facility;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 코스 안의 시설 하나 + 순서. 스톱 시각은 여기 저장하지 않고 조회 시 순서로부터 매번 계산한다
// (첫 스톱 10:00, 스톱당 +90분 — CourseConverter 참고).
@Getter
@Entity
@Table(name = "course_stops")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Column(name = "stop_order", nullable = false)
    private int stopOrder;

    @Builder
    private CourseStop(
            Course course,
            Facility facility,
            int stopOrder
    ) {
        this.course = course;
        this.facility = facility;
        this.stopOrder = stopOrder;
    }

}
