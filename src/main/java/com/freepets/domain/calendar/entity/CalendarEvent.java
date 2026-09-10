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

    // 반복 없음(NONE)이면 유일한 발생일(기간 일정이면 그 기간의 시작일). 반복 있으면 발생 계산의
    // 기준일(anchor)이다 — WEEKLY의 요일, MONTHLY의 "며칠"이 전부 이 날짜에서 파생된다.
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    // 여행처럼 며칠에 걸치는 일정의 종료일. 기간이 없는 단일 일정(대부분의 MED/VACCINE 등)은
    // startDate와 같은 값으로 채운다 — 그래서 null 체크 없이 항상 [startDate, endDate] 구간으로
    // 다룰 수 있다("기간 없음"은 길이 1일짜리 구간). 반복 일정(repeatType != NONE)엔 기간 개념을
    // 두지 않는다 — CalendarEventCommandService가 생성/수정 시점에 막는다(CALENDAR4007).
    //
    // DB 컬럼은 nullable로 둔다 — 기존 라이브 행엔 채울 값이 하나로 정해지지 않아(행마다
    // start_date를 그대로 복사해야 함) Course.isPublic류의 고정 @ColumnDefault를 못 쓴다.
    // db/pending-manual-migrations.sql에 백필 스크립트를 남겨뒀고, 백필 전 레거시 행은
    // getEndDate()가 startDate로 대체해서 애플리케이션 계층에서는 항상 값이 있는 것처럼 다룬다.
    @Column(name = "end_date")
    private LocalDate endDate;

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

    // 여행 기록(자동/수동 등록 모두)에 사진 한 장을 붙일 수 있다. Pet.profile과 같은 방식으로
    // S3에 올린 뒤 URL만 저장한다 — 여러 장 첨부는 지금 범위 밖이라 단일 필드로 둔다.
    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;

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
            LocalDate endDate,
            LocalTime eventTime,
            RepeatType repeatType,
            boolean reminderEnabled,
            String notes,
            String photoUrl
    ) {
        this.user = user;
        this.pet = pet;
        this.eventType = eventType;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate != null ? endDate : startDate;
        this.eventTime = eventTime;
        this.repeatType = repeatType;
        this.reminderEnabled = reminderEnabled;
        this.notes = notes;
        this.photoUrl = photoUrl;
    }

    public void update(
            Pet pet,
            CalendarEventType eventType,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            LocalTime eventTime,
            RepeatType repeatType,
            boolean reminderEnabled,
            String notes,
            String photoUrl
    ) {
        this.pet = pet;
        this.eventType = eventType;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate != null ? endDate : startDate;
        this.eventTime = eventTime;
        this.repeatType = repeatType;
        this.reminderEnabled = reminderEnabled;
        this.notes = notes;
        this.photoUrl = photoUrl;
    }

    public void toggleReminder(boolean reminderEnabled) {
        this.reminderEnabled = reminderEnabled;
    }

    public boolean isOwnedBy(Long userId) {
        return user != null && user.getId().equals(userId);
    }

    /** 백필 전 레거시 행(endDate 컬럼이 아직 null)은 startDate로 대체한다 — 생성자/update()를
     * 거친 행은 항상 이미 채워져 있어 사실상 이 대체가 필요 없지만, 방어적으로 둔다. */
    public LocalDate getEndDate() {
        return endDate != null ? endDate : startDate;
    }

}
