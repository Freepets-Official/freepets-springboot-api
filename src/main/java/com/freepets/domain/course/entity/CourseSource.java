package com.freepets.domain.course.entity;

/**
 * 코스가 어떻게 만들어졌는지.
 */
public enum CourseSource {

    /** 사용자가 직접 담은 코스. */
    CUSTOM,

    /** 지역×테마 조합으로 서버가 배치 계산해 캐시해둔 코스. {@link Course#getUser()}는 null. */
    PRESET,

    /** 우리 아이 취향/취향 비슷한 새곳 — 요청마다 재계산하는 개인화 추천. DB에 저장하지 않는다. */
    RECOMMENDED

}
