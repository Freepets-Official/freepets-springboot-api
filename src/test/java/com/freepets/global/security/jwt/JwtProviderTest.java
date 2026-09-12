package com.freepets.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtProviderTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtcHJvdmlkZXItdW5pdC10ZXN0";

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(14));
        jwtProvider = new JwtProvider(jwtProperties);
        ReflectionTestUtils.invokeMethod(jwtProvider, "init");
    }

    @Test
    void 발급한_액세스_토큰에서_유저_아이디를_그대로_복원한다() {
        String token = jwtProvider.createAccessToken(1L);

        Long userId = jwtProvider.getUserIdFromAccessToken(token);

        assertThat(userId).isEqualTo(1L);
    }

    @Test
    void 만료된_토큰이면_TOKEN4002_예외를_던진다() {
        SecretKey signingKey = (SecretKey) ReflectionTestUtils.getField(jwtProvider, "signingKey");
        Date past = new Date(System.currentTimeMillis() - Duration.ofMinutes(1).toMillis());
        String expiredToken = Jwts.builder()
                .subject("1")
                .issuedAt(new Date(past.getTime() - Duration.ofMinutes(30).toMillis()))
                .expiration(past)
                .signWith(signingKey)
                .compact();

        assertThatThrownBy(() -> jwtProvider.getUserIdFromAccessToken(expiredToken))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorStatus.TOKEN4002);
    }

    @Test
    void 다른_키로_서명된_토큰이면_TOKEN4001_예외를_던진다() {
        SecretKey otherKey = Keys.hmacShaKeyFor(SECRET.repeat(2).getBytes());
        String tokenSignedWithOtherKey = Jwts.builder()
                .subject("1")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + Duration.ofMinutes(30).toMillis()))
                .signWith(otherKey)
                .compact();

        assertThatThrownBy(() -> jwtProvider.getUserIdFromAccessToken(tokenSignedWithOtherKey))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorStatus.TOKEN4001);
    }

    @Test
    void 형식이_깨진_토큰이면_TOKEN4001_예외를_던진다() {
        String malformedToken = jwtProvider.createAccessToken(1L) + "tampered";

        assertThatThrownBy(() -> jwtProvider.getUserIdFromAccessToken(malformedToken))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorStatus.TOKEN4001);
    }

    @Test
    void 빈_토큰이면_TOKEN4001_예외를_던진다() {
        assertThatThrownBy(() -> jwtProvider.getUserIdFromAccessToken(""))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorStatus.TOKEN4001);
    }
    @Test
    void 리프레시_토큰을_액세스_토큰_자리에_쓰면_TOKEN4003_예외를_던진다() {
        String refreshToken = jwtProvider.createRefreshToken(1L);

        assertThatThrownBy(() -> jwtProvider.getUserIdFromAccessToken(refreshToken))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorStatus.TOKEN4003);
    }

    @Test
    void 액세스_토큰으로_재발급을_시도하면_TOKEN4003_예외를_던진다() {
        String accessToken = jwtProvider.createAccessToken(1L);

        assertThatThrownBy(() -> jwtProvider.getUserIdFromRefreshToken(accessToken))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorStatus.TOKEN4003);
    }

    @Test
    void 발급한_리프레시_토큰에서_유저_아이디를_그대로_복원한다() {
        String refreshToken = jwtProvider.createRefreshToken(7L);

        Long userId = jwtProvider.getUserIdFromRefreshToken(refreshToken);

        assertThat(userId).isEqualTo(7L);
    }

    @Test
    void 용도_클레임이_없는_예전_토큰은_TOKEN4003_예외를_던진다() {
        SecretKey signingKey = (SecretKey) ReflectionTestUtils.getField(jwtProvider, "signingKey");
        String legacyToken = Jwts.builder()
                .subject("1")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + Duration.ofMinutes(30).toMillis()))
                .signWith(signingKey)
                .compact();

        assertThatThrownBy(() -> jwtProvider.getUserIdFromAccessToken(legacyToken))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorStatus.TOKEN4003);
    }
    @Test
    void 만료된_리프레시_토큰이면_TOKEN4004_예외를_던진다() {
        SecretKey signingKey = (SecretKey) ReflectionTestUtils.getField(jwtProvider, "signingKey");
        Date past = new Date(System.currentTimeMillis() - Duration.ofMinutes(1).toMillis());
        String expiredRefreshToken = Jwts.builder()
                .subject("1")
                .claim("tokenType", "REFRESH")
                .issuedAt(new Date(past.getTime() - Duration.ofDays(14).toMillis()))
                .expiration(past)
                .signWith(signingKey)
                .compact();

        assertThatThrownBy(() -> jwtProvider.getUserIdFromRefreshToken(expiredRefreshToken))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorStatus.TOKEN4004);
    }
}
