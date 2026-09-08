package com.freepets.global.config;

import java.time.LocalDateTime;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * {@code BaseEntity.createdAt}/{@code updatedAt}을 비롯해 응답에 노출되는 모든
 * {@link LocalDateTime}에 UTC 오프셋("Z")을 붙여 직렬화한다.
 *
 * <p>{@code LocalDateTime}은 타임존 개념이 없어서 그냥 직렬화하면 "2026-09-07T16:07:22.634035"처럼
 * 오프셋 없는 문자열이 나간다 — ECMAScript 규격상 이런 문자열은 클라이언트에서 로컬 시각으로
 * 해석돼 실제보다 몇 시간씩 어긋난 시각이 표시된다(프론트 연동 중 발견).
 *
 * <p>{@link com.freepets.FreepetsServerApplication}이 JVM 기본 타임존을 UTC로 고정해둬서
 * 이 값은 항상 UTC를 담고 있다고 보장되므로, 여기서는 "Z"만 붙이면 된다. 엔티티 필드 타입을
 * {@code Instant}/{@code OffsetDateTime}으로 바꾸는 게 더 근본적인 해결책이지만, 리포 전체에서
 * {@code LocalDateTime.now().minusHours(...)} 같은 비교 로직이 광범위하게 쓰이고 있어(24시간
 * 재제보 제한 등) 그 변경은 범위가 훨씬 크다 — 직렬화 레이어에서만 고치는 쪽이 훨씬 안전하다.
 *
 * <p>실제로 HTTP 응답 직렬화에 쓰이는 컨버터가 {@code JacksonJsonHttpMessageConverter}라는
 * 걸 {@code WebMvcConfigurer.configureMessageConverters}로 등록된 컨버터 목록을 직접 찍어보고
 * 확인했다 — 이 리포는 코드 곳곳에서 Jackson 2({@code com.fasterxml.jackson.*})도 같이 쓰지만,
 * 실제 API 응답에 쓰이는 건 Jackson 3({@code tools.jackson.*}) 기반인 {@code JsonMapper}다.
 * 그래서 흔히 아는 {@code Jackson2ObjectMapperBuilderCustomizer}/{@code SimpleModule} 빈
 * 등록(Jackson 2 API)은 이 프로젝트에서 효과가 없고, Jackson 3용 {@link JsonMapperBuilderCustomizer}를
 * 써야 한다.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer utcLocalDateTimeCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(LocalDateTime.class, new ValueSerializer<LocalDateTime>() {
                @Override
                public void serialize(
                        LocalDateTime value,
                        JsonGenerator gen,
                        SerializationContext ctxt
                ) throws JacksonException {
                    gen.writeString(value.toString() + "Z");
                }
            });
            builder.addModule(module);
        };
    }
}
