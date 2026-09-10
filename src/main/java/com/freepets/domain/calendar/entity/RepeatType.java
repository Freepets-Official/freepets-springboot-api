package com.freepets.domain.calendar.entity;

// 반복 규칙. 종료일은 없다 — startDate부터 무기한 반복된다(요구사항에 "언제까지"가 없어서 지금은
// 이렇게 두고, 필요해지면 nullable 종료일 컬럼을 추가하면 된다).
public enum RepeatType {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
}
