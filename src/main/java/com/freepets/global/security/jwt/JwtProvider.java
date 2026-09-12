package com.freepets.global.security.jwt;

import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    private final JwtProperties jwtProperties;

    private SecretKey signingKey;
    private JwtParser jwtParser;

    @PostConstruct
    private void init() {
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
        jwtParser = Jwts.parser().verifyWith(signingKey).build();
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, TokenType.ACCESS, jwtProperties.accessTokenExpiration());
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, TokenType.REFRESH, jwtProperties.refreshTokenExpiration());
    }

    /**
     * 인증(Authorization 헤더) 전용. 리프레시 토큰을 넣으면 거부한다.
     */
    public Long getUserIdFromAccessToken(String token) {
        return getUserId(token, TokenType.ACCESS, ErrorStatus.TOKEN4002);
    }

    /**
     * 액세스 토큰 재발급 전용. 액세스 토큰을 넣으면 거부한다 — 받아주면 액세스 토큰만으로
     * 무한히 재발급받을 수 있어 만료 기간이 사실상 없어진다.
     */
    public Long getUserIdFromRefreshToken(String token) {
        return getUserId(token, TokenType.REFRESH, ErrorStatus.TOKEN4004);
    }

    private Long getUserId(
            String token,
            TokenType expectedTokenType,
            ErrorStatus expiredErrorStatus
    ) {
        Claims claims = parseClaims(token, expiredErrorStatus);

        // 용도 클레임이 없는 토큰은 이 클레임이 생기기 전에 발급된 것이다. 액세스인지
        // 리프레시인지 구분할 방법이 없으므로 둘 다 아닌 것으로 보고 거부한다.
        if (!expectedTokenType.name().equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new GeneralException(ErrorStatus.TOKEN4003);
        }

        return Long.valueOf(claims.getSubject());
    }

    /**
     * @param expiredErrorStatus 만료됐을 때 쓸 코드. 액세스 토큰 만료({@code TOKEN4002})는 앱이
     *                           재발급으로, 리프레시 토큰 만료({@code TOKEN4004})는 재로그인으로
     *                           가야 해서 둘을 같은 코드로 내리면 앱이 다음 행동을 고를 수 없다
     */
    private Claims parseClaims(
            String token,
            ErrorStatus expiredErrorStatus
    ) {
        try {
            return jwtParser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException exception) {
            throw new GeneralException(expiredErrorStatus);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new GeneralException(ErrorStatus.TOKEN4001);
        }
    }

    private String createToken(
            Long userId,
            TokenType tokenType,
            Duration expiration
    ) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration.toMillis());

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, tokenType.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }
}
