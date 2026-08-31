package com.freepets.global.util;

/**
 * 숫자를 화면에 내려보내기 좋은 형태로 다듬는다.
 */
public class Numbers {

    private Numbers() {}

    /**
     * 소수 한 자리로 반올림한다.
     *
     * <p>평점·점수를 응답에 담기 직전에만 쓴다. 등급 판정처럼 임계값과 비교하는 계산에는
     * 반올림하지 않은 원값을 넘겨야 한다. 87.96이 88.0이 되면서 한 등급 올라가면 안 된다.
     */
    public static double roundToOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }

}
