package com.freepets.domain.petcheck.service;

import java.security.SecureRandom;

/**
 * 동반 출입증 검증(GET /verify/{code}) 코드 생성기.
 *
 * <p>프론트 목업의 {@code passIssueCode}는 {@code checkId*31 + petId*17}을 20비트로
 * 접어넣는 선형식이라 (1) 서로 다른 판별이 같은 코드를 낼 수 있고 (2) 역산으로 다른 사람의
 * 코드를 추측할 수 있다. 이 페이지는 인증 없는 공개 웹페이지이고 반려동물 이름·체중·접종
 * 여부까지 보여주므로, 코드 하나가 곧 열람 권한이다 — 그래서 원본과 무관한 CSPRNG 문자열을
 * 서버가 직접 발급한다.
 */
public final class VerifyCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    // 10자(36^10 ≈ 3.7×10^15)는 생일 역설로 계산하면 판별 약 1천만 건 근처부터 충돌 확률이
    // 무시 못 할 수준(~1%)이 된다. 저장 전 충돌 검사(existsByVerifyCode 등)는 일부러 안 넣었다
    // — 이 메서드가 정적 유틸이라 리포지토리 의존성을 새로 들여야 하고, 충돌 시 unique 제약
    // 위반으로 판별 요청 전체가 500으로 실패하는 것도 감수할 수준까지 충분히 자리를 늘리는
    // 쪽이 더 단순하다. 12자로 자리를 넉넉히 키워 그 실패 자체가 사실상 안 일어나게 만든다.
    private static final int RANDOM_PART_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private VerifyCodeGenerator() {}

    /** "FP-" + 영대문자/숫자 12자. 36^12(약 4.7×10^18) 공간이라 브루트포스·충돌 둘 다 비현실적이다. */
    public static String generate() {
        StringBuilder randomPart = new StringBuilder(RANDOM_PART_LENGTH);
        for (int i = 0; i < RANDOM_PART_LENGTH; i++) {
            randomPart.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return "FP-" + randomPart;
    }
}
