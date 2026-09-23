package com.freepets.domain.course.entity;

import java.time.LocalTime;

import com.freepets.domain.facility.entity.Facility;

/**
 * 코스에 담을 스톱 하나의 내용 — 어느 시설에 몇 시에 가는지. 순서는 담기는 위치가 정하므로
 * 여기 없다.
 *
 * <p>{@link Course#replaceStops}에 시설 목록만 넘기던 것을 이 타입으로 바꿨다. 시설 목록과
 * 시각 목록을 따로 넘기면 두 목록의 길이·순서가 어긋났을 때 엉뚱한 스톱에 시각이 붙는데,
 * 그 어긋남이 조용히 저장까지 통과한다.
 */
public record CourseStopDraft(
        Facility facility,
        LocalTime visitTime
) {

    /** 시각 개념이 없는 경로(PRESET 나이틀리 재계산 등)에서 쓴다. */
    public static CourseStopDraft withoutTime(Facility facility) {
        return new CourseStopDraft(facility, null);
    }

}
