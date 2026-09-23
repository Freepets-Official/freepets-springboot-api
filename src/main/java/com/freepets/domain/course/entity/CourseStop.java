package com.freepets.domain.course.entity;

import java.time.LocalTime;

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

// 코스 안의 시설 하나 + 순서 + 도착 시각.
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

    /**
     * 이 스톱에 몇 시에 도착하는지. 사용자가 안 정했으면 비어 있다 — 기존 코스에는 값이 없고,
     * 시간을 안 쓰는 PRESET 코스도 비운 채로 둔다.
     */
    @Column(name = "visit_time")
    private LocalTime visitTime;

    @Builder
    private CourseStop(
            Course course,
            Facility facility,
            int stopOrder,
            LocalTime visitTime
    ) {
        this.course = course;
        this.facility = facility;
        this.stopOrder = stopOrder;
        this.visitTime = visitTime;
    }

}
