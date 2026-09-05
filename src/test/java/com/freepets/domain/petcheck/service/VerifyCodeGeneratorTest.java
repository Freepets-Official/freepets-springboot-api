package com.freepets.domain.petcheck.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class VerifyCodeGeneratorTest {

    @Test
    void FP_접두사와_영대문자_숫자_12자로_생성된다() {
        String code = VerifyCodeGenerator.generate();

        assertThat(code).matches("FP-[A-Z0-9]{12}");
    }

    @Test
    void 호출할_때마다_다른_코드가_나온다() {
        // 완전한 무충돌 보장 테스트는 아니다(36^12 공간에서 우연히 겹칠 수는 있음) — 짧게
        // 여러 번 뽑아도 전부 달라야 한다는 실용적인 회귀 검증이다.
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(VerifyCodeGenerator.generate());
        }

        assertThat(codes).hasSize(1000);
    }
}
