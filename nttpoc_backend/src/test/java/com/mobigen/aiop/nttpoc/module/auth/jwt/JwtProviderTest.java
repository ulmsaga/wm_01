package com.mobigen.aiop.nttpoc.module.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtProvider")
class JwtProviderTest {

    // "12345678901234567890123456789012" → 32바이트 → Base64
    private static final String VALID_SECRET = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=";
    private static final String ISSUER = "nttpoc";
    private static final String USER_ID = "user-001";

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(ISSUER, VALID_SECRET, 30, 30);
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("생성자")
    class Constructor {

        @Test
        @DisplayName("32바이트 미만 시크릿 → IllegalArgumentException")
        void shortSecret_throwsIllegalArgumentException() {
            String shortSecret = Base64.getEncoder().encodeToString("short".getBytes());

            assertThatThrownBy(() -> new JwtProvider(ISSUER, shortSecret, 30, 30))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("32 bytes");
        }

        @Test
        @DisplayName("유효한 시크릿 → 정상 생성")
        void validSecret_created() {
            assertThatCode(() -> new JwtProvider(ISSUER, VALID_SECRET, 30, 30))
                    .doesNotThrowAnyException();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("issueTokenPair")
    class IssueTokenPair {

        @Test
        @DisplayName("AT / RT 토큰 문자열 non-blank 반환")
        void returnsNonBlankTokens() {
            JwtProvider.TokenPair pair = jwtProvider.issueTokenPair(USER_ID, Map.of());

            assertThat(pair.accessToken()).isNotBlank();
            assertThat(pair.refreshToken()).isNotBlank();
        }

        @Test
        @DisplayName("AT sub = userId")
        void accessTokenSubject() {
            JwtProvider.TokenPair pair = jwtProvider.issueTokenPair(USER_ID, Map.of());
            Claims claims = jwtProvider.parseAndValidate(pair.accessToken(), JwtProvider.TYP_ACCESS);

            assertThat(claims.getSubject()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("AT typ = access")
        void accessTokenTyp() {
            JwtProvider.TokenPair pair = jwtProvider.issueTokenPair(USER_ID, Map.of());
            Claims claims = jwtProvider.parseAndValidate(pair.accessToken(), JwtProvider.TYP_ACCESS);

            assertThat(claims.get(JwtProvider.CLAIM_TYP, String.class)).isEqualTo(JwtProvider.TYP_ACCESS);
        }

        @Test
        @DisplayName("RT typ = refresh")
        void refreshTokenTyp() {
            JwtProvider.TokenPair pair = jwtProvider.issueTokenPair(USER_ID, Map.of());
            Claims claims = jwtProvider.parseAndValidate(pair.refreshToken(), JwtProvider.TYP_REFRESH);

            assertThat(claims.get(JwtProvider.CLAIM_TYP, String.class)).isEqualTo(JwtProvider.TYP_REFRESH);
        }

        @Test
        @DisplayName("AT jti != RT jti (고유 식별자)")
        void jtiAreDistinct() {
            JwtProvider.TokenPair pair = jwtProvider.issueTokenPair(USER_ID, Map.of());

            assertThat(pair.accessJti()).isNotEqualTo(pair.refreshJti());
        }

        @Test
        @DisplayName("extraClaims가 AT에 포함됨")
        void extraClaimsInAccessToken() {
            JwtProvider.TokenPair pair = jwtProvider.issueTokenPair(USER_ID, Map.of("role", "ADMIN"));
            Claims claims = jwtProvider.parseAndValidate(pair.accessToken(), JwtProvider.TYP_ACCESS);

            assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("extraClaims가 RT에 포함되지 않음")
        void extraClaimsNotInRefreshToken() {
            JwtProvider.TokenPair pair = jwtProvider.issueTokenPair(USER_ID, Map.of("role", "ADMIN"));
            Claims claims = jwtProvider.parseAndValidate(pair.refreshToken(), JwtProvider.TYP_REFRESH);

            assertThat(claims.get("role")).isNull();
        }

        @Test
        @DisplayName("accessExpiresAt < refreshExpiresAt")
        void accessExpiresBeforeRefresh() {
            JwtProvider.TokenPair pair = jwtProvider.issueTokenPair(USER_ID, Map.of());

            assertThat(pair.accessExpiresAt()).isNotNull();
            assertThat(pair.refreshExpiresAt()).isNotNull();
            assertThat(pair.accessExpiresAt()).isBefore(pair.refreshExpiresAt());
        }

        @Test
        @DisplayName("null extraClaims → NullPointerException 없이 정상 발급")
        void nullExtraClaims_doesNotThrow() {
            assertThatCode(() -> jwtProvider.issueTokenPair(USER_ID, null))
                    .doesNotThrowAnyException();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("parseAndValidate")
    class ParseAndValidate {

        @Test
        @DisplayName("유효한 AT → claims 정상 반환")
        void validAccessToken_returnsClaims() {
            JwtProvider.TokenPair pair = jwtProvider.issueTokenPair(USER_ID, Map.of());

            assertThatCode(() -> jwtProvider.parseAndValidate(pair.accessToken(), JwtProvider.TYP_ACCESS))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("유효한 RT → claims 정상 반환")
        void validRefreshToken_returnsClaims() {
            JwtProvider.TokenPair pair = jwtProvider.issueTokenPair(USER_ID, Map.of());

            assertThatCode(() -> jwtProvider.parseAndValidate(pair.refreshToken(), JwtProvider.TYP_REFRESH))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("typ 불일치 (AT를 RT로 검증) → JwtException")
        void typMismatch_throwsJwtException() {
            JwtProvider.TokenPair pair = jwtProvider.issueTokenPair(USER_ID, Map.of());

            assertThatThrownBy(() -> jwtProvider.parseAndValidate(pair.accessToken(), JwtProvider.TYP_REFRESH))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("서명 위조 토큰 → JwtException")
        void tamperedSignature_throwsJwtException() {
            JwtProvider.TokenPair pair = jwtProvider.issueTokenPair(USER_ID, Map.of());
            String tampered = pair.accessToken() + "tampered";

            assertThatThrownBy(() -> jwtProvider.parseAndValidate(tampered, JwtProvider.TYP_ACCESS))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("만료 토큰 → ExpiredJwtException")
        void expiredToken_throwsExpiredJwtException() {
            String expiredToken = buildExpiredToken(VALID_SECRET, ISSUER, USER_ID, JwtProvider.TYP_ACCESS);

            assertThatThrownBy(() -> jwtProvider.parseAndValidate(expiredToken, JwtProvider.TYP_ACCESS))
                    .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("다른 시크릿으로 서명된 토큰 → JwtException")
        void wrongKey_throwsJwtException() {
            // 다른 시크릿(32바이트)으로 생성한 토큰
            String otherSecret = Base64.getEncoder()
                    .encodeToString("OtherSecret1234567890123456789012".getBytes());
            JwtProvider other = new JwtProvider(ISSUER, otherSecret, 30, 30);
            JwtProvider.TokenPair pair = other.issueTokenPair(USER_ID, Map.of());

            assertThatThrownBy(() -> jwtProvider.parseAndValidate(pair.accessToken(), JwtProvider.TYP_ACCESS))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("빈 문자열 토큰 → JwtException 계열 예외")
        void emptyToken_throwsException() {
            assertThatThrownBy(() -> jwtProvider.parseAndValidate("", JwtProvider.TYP_ACCESS))
                    .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("getSubject / getJti")
    class Accessors {

        @Test
        @DisplayName("getSubject → userId 반환")
        void getSubject_returnsUserId() {
            JwtProvider.TokenPair pair = jwtProvider.issueTokenPair(USER_ID, Map.of());
            Claims claims = jwtProvider.parseAndValidate(pair.accessToken(), JwtProvider.TYP_ACCESS);

            assertThat(jwtProvider.getSubject(claims)).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("getJti → accessJti와 일치")
        void getJti_matchesAccessJti() {
            JwtProvider.TokenPair pair = jwtProvider.issueTokenPair(USER_ID, Map.of());
            Claims claims = jwtProvider.parseAndValidate(pair.accessToken(), JwtProvider.TYP_ACCESS);

            assertThat(jwtProvider.getJti(claims)).isEqualTo(pair.accessJti());
        }
    }

    // -------------------------------------------------------------------------
    // 헬퍼: 만료된 JWT 직접 생성
    private static String buildExpiredToken(String secret, String issuer, String sub, String typ) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        Key key = Keys.hmacShaKeyFor(keyBytes);

        Instant past = Instant.now().minusSeconds(60);
        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(sub)
                .setIssuedAt(Date.from(past.minusSeconds(60)))
                .setExpiration(Date.from(past))
                .claim(JwtProvider.CLAIM_TYP, typ)
                .claim(JwtProvider.CLAIM_JTI, "expired-jti")
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
