package com.freepets.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

// 프론트 연동 중 발견 — createdAt/updatedAt(LocalDateTime)이 오프셋 없이 직렬화돼서
// 클라이언트가 로컬 시각으로 잘못 해석하던 문제. UTC로 고정한 뒤 "Z"를 붙이는 커스텀
// 직렬화가 실제 HTTP 응답에 쓰이는 JsonMapper(Jackson 3)에 반영되는지 검증한다.
class JacksonConfigTest {

    @Test
    void LocalDateTime을_직렬화하면_UTC_오프셋_Z가_붙는다() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().utcLocalDateTimeCustomizer().customize(builder);
        JsonMapper jsonMapper = builder.build();

        LocalDateTime dateTime = LocalDateTime.of(2026, 9, 7, 16, 7, 22, 634035000);

        String json = jsonMapper.writeValueAsString(dateTime);

        assertThat(json).isEqualTo("\"2026-09-07T16:07:22.634035Z\"");
    }
}
