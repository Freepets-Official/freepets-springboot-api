package com.freepets.domain.user.entity;

/**
 * 계정의 권한. 가입하면 전원 {@code USER}이고, 운영자만 {@code ADMIN}이다.
 *
 * <p>화면 세트인 {@link Profile}과는 다른 축이다 — 프로필은 소유 기록에서 파생하는 UI 상태일 뿐
 * 권한이 아니지만, 역할은 {@code /api/v1/admin/**} 접근을 가르는 실제 권한이다.
 *
 * <p>관리자로 지정하는 API는 두지 않는다. 운영자가 DB에서 직접 바꾼다
 * ({@code db/pending-manual-migrations.sql} 참고). 토큰에도 넣지 않고 요청마다 DB에서 읽어서,
 * 권한을 주거나 회수하면 재로그인 없이 바로 반영된다({@code JwtAuthenticationFilter}).
 */
public enum Role {
    USER,
    ADMIN
}
