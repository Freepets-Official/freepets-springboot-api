package com.freepets.global.security.jwt;

/**
 * 토큰의 용도. {@code tokenType} 클레임으로 토큰에 박아두고 검증할 때 대조한다.
 *
 * <p>이 구분이 없으면 액세스 토큰과 리프레시 토큰의 클레임이 완전히 같아져서, 만료 기간만
 * 다른 같은 토큰이 된다. 그러면 리프레시 토큰을 {@code Authorization} 헤더에 넣는 것만으로
 * 유효기간이 훨씬 긴 액세스 토큰처럼 쓸 수 있고, 반대로 재발급 API가 액세스 토큰을 받아
 * 무한히 갱신해주게 된다.
 */
public enum TokenType {

    ACCESS,
    REFRESH
}
