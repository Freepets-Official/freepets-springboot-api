package com.freepets.domain.calendar.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// MED 유형 일정의 날짜별 복용 체크. "행의 존재 여부 = 그날 복용함"으로 설계했다 — 별도 taken
// boolean을 두면 false 상태를 남길지 지울지 이중으로 관리해야 해서, 체크(PUT)/해제(DELETE)를
// 이 행의 생성/삭제로 그대로 표현한다. Column createdAt/updatedAt이 필요 없어 BaseEntity는
// 상속하지 않는다(PetSatisfaction과 같은 이유).
@Getter
@Entity
@Table(
        name = "calendar_med_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_calendar_med_logs_event_date",
                columnNames = {"event_id", "date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarMedLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "med_log_id")
    private Long medLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private CalendarEvent event;

    @Column(nullable = false)
    private LocalDate date;

    @Builder
    private CalendarMedLog(
            CalendarEvent event,
            LocalDate date
    ) {
        this.event = event;
        this.date = date;
    }

}
