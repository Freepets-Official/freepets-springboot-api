package com.freepets.domain.facility.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ConfidenceTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 9, 1, 10, 0);
    private static final String CONDITION_RAW = "리드줄 착용 시 동반 가능";

    @Test
    void 사업자가_확정했으면_확정으로_표시한다() {
        Confidence.View view = Confidence.of(CONDITION_RAW, 0, CONFIRMED_AT);

        assertThat(view.confidence()).isEqualTo(Confidence.CONFIRMED);
        assertThat(view.source()).isEqualTo(ConfidenceSource.OWNER);
    }

    @Test
    void 확정_이후_들어온_거부_제보는_확정을_이긴다() {
        // 호출부가 확정 이후 제보만 세서 넘긴다. 사장님이 적어둔 조건과 현장이 다르다는 신호라
        // 배지를 유지하면 헛걸음으로 이어진다.
        Confidence.View view = Confidence.of(CONDITION_RAW, 1, CONFIRMED_AT);

        assertThat(view.confidence()).isEqualTo(Confidence.UNVERIFIED);
        assertThat(view.source()).isEqualTo(ConfidenceSource.DENIAL_REPORT);
    }

    @Test
    void 확정한_적이_없고_조건_안내문만_있으면_추정이다() {
        Confidence.View view = Confidence.of(CONDITION_RAW, 0, null);

        assertThat(view.confidence()).isEqualTo(Confidence.ESTIMATED);
        assertThat(view.source()).isEqualTo(ConfidenceSource.PARSED);
    }

    @Test
    void 아무_신호도_없으면_미확인이다() {
        Confidence.View view = Confidence.of(null, 0, null);

        assertThat(view.confidence()).isEqualTo(Confidence.UNVERIFIED);
        assertThat(view.source()).isEqualTo(ConfidenceSource.NONE);
    }

    @Test
    void 조건_안내문이_공백뿐이면_신호로_치지_않는다() {
        Confidence.View view = Confidence.of("   ", 0, null);

        assertThat(view.confidence()).isEqualTo(Confidence.UNVERIFIED);
        assertThat(view.source()).isEqualTo(ConfidenceSource.NONE);
    }
}
