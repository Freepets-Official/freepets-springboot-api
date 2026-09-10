package com.freepets.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CourseShareCodeGeneratorTest {

    @Test
    void CRS_접두사와_영대문자_숫자_10자로_생성된다() {
        String code = CourseShareCodeGenerator.generate();

        assertThat(code).matches("CRS-[A-Z0-9]{10}");
    }

    @Test
    void 호출할_때마다_다른_코드가_나온다() {
        // 완전한 무충돌 보장 테스트는 아니다(36^10 공간에서 우연히 겹칠 수는 있음) — 짧게
        // 여러 번 뽑아도 전부 달라야 한다는 실용적인 회귀 검증이다.
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(CourseShareCodeGenerator.generate());
        }

        assertThat(codes).hasSize(1000);
    }
}
