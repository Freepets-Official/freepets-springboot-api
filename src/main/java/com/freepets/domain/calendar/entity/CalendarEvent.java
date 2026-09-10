package com.freepets.domain.calendar.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.user.entity.User;
import com.freepets.global.entity.BaseEntity;

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
import org.hibernate.annotations.ColumnDefault;

// 반려동물 캘린더 일정(예방접종/약 복용/건강검진/여행/기타). "본인 전용"이라 소유자는 pet이 아니라
// user를 직접 참조한다 — pet이 null이면(화면의 "전체" 옵션) 특정 반려동물이 아니라 이 유저의
// 모든 반려동물에 적용되는 일정이라는 뜻이라, pet을 거쳐 소유권을 따지면 이 경우를 표현할 수 없다.
@Getter
@Entity
@Table(name = "calendar_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // null이면 "전체" — 특정 반려동물이 아니라 이 유저의 모든 반려동물에 적용되는 일정.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private CalendarEventType eventType;

    @Column(length = 100, nullable = false)
    private String title;

    // 반복 없음(NONE)이면 유일한 발생일. 반복 있으면 발생 계산의 기준일(anchor)이다 —
    // WEEKLY의 요일, MONTHLY의 "며칠"이 전부 이 날짜에서 파생된다.
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "event_time")
    private LocalTime eventTime;

    @ColumnDefault("'NONE'")
    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_type", nullable = false, length = 10)
    private RepeatType repeatType;

    @ColumnDefault("false")
    @Column(name = "reminder_enabled", nullable = false)
    private boolean reminderEnabled;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // 이벤트 삭제 시 그날그날 복용 체크 기록도 함께 지우는 용도로만 둔다 — 실제 조회/저장은
    // 항상 CalendarMedLogRepository를 직접 통해서 하고 이 컬렉션을 읽지는 않는다. 같은
    // 트랜잭션에서 리포지토리 직접 저장과 이 컬렉션이 따로 노는 걸 피하기 위함이다.
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CalendarMedLog> medLogs = new ArrayList<>();

    @Builder
    private CalendarEvent(
            User user,
            Pet pet,
            CalendarEventType eventType,
            String title,
            LocalDate startDate,
            LocalTime eventTime,
            RepeatType repeatType,
            boolean reminderEnabled,
            String notes
    ) {
        this.user = user;
        this.pet = pet;
        this.eventType = eventType;
        this.title = title;
        this.startDate = startDate;
        this.eventTime = eventTime;
        this.repeatType = repeatType;
        this.reminderEnabled = reminderEnabled;
        this.notes = notes;
    }

    public void update(
            Pet pet,
            CalendarEventType eventType,
            String title,
            LocalDate startDate,
            LocalTime eventTime,
            RepeatType repeatType,
            boolean reminderEnabled,
            String notes
    ) {
        this.pet = pet;
        this.eventType = eventType;
        this.title = title;
        this.startDate = startDate;
        this.eventTime = eventTime;
        this.repeatType = repeatType;
        this.reminderEnabled = reminderEnabled;
        this.notes = notes;
    }

    public void toggleReminder(boolean reminderEnabled) {
        this.reminderEnabled = reminderEnabled;
    }

    public boolean isOwnedBy(Long userId) {
        return user != null && user.getId().equals(userId);
    }

}
