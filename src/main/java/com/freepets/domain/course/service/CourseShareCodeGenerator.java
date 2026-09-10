package com.freepets.domain.course.service;

import java.security.SecureRandom;

/**
 * 코스 공유 코드 생성기. {@code com.freepets.domain.petcheck.service.VerifyCodeGenerator}와 같은
 * 패턴이지만, 도메인끼리 서로의 서비스 클래스를 참조하지 않는다는 패키지 컨벤션(CLAUDE.md)에
 * 따라 course 도메인 소속으로 따로 둔다.
 *
 * <p>이 코드를 아는 사람은 누구나 그 코스를 자기 코스로 복사할 수 있다 — 코드 하나가 곧 복사
 * 권한이라 원본과 무관한 CSPRNG 문자열을 서버가 직접 발급한다.
 */
public final class CourseShareCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    // 8자(36^8 ≈ 2.8×10^12)면 충분하지만, VerifyCodeGenerator와 같은 이유로 저장 전 충돌 검사
    // (existsByShareCode 등)는 넣지 않았다 — 정적 유틸에 리포지토리 의존성을 새로 들이는 대신
    // 자리를 넉넉히 키워 충돌 자체가 사실상 안 일어나게 만드는 쪽을 택했다.
    private static final int RANDOM_PART_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CourseShareCodeGenerator() {}

    /** "CRS-" + 영대문자/숫자 10자. 36^10(약 3.7×10^15) 공간이라 브루트포스·충돌 둘 다 비현실적이다. */
    public static String generate() {
        StringBuilder randomPart = new StringBuilder(RANDOM_PART_LENGTH);
        for (int i = 0; i < RANDOM_PART_LENGTH; i++) {
            randomPart.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return "CRS-" + randomPart;
    }
}
