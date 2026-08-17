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

    private final JwtProperties jwtProperties;

    private SecretKey signingKey;
    private JwtParser jwtParser;

    @PostConstruct
    private void init() {
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
        jwtParser = Jwts.parser().verifyWith(signingKey).build();
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, jwtProperties.accessTokenExpiration());
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, jwtProperties.refreshTokenExpiration());
    }

    public Long getUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    private Claims parseClaims(String token) {
        try {
            return jwtParser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException exception) {
            throw new GeneralException(ErrorStatus.TOKEN4002);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new GeneralException(ErrorStatus.TOKEN4001);
        }
    }

    private String createToken(
            Long userId,
            Duration expiration
    ) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration.toMillis());

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }
}
