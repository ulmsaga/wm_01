package com.saga.wm.module.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

/**
 * Access/Refresh JWT 발급 & 검증
 *
 * - Access: 짧게 (ex: 30분)
 * - Refresh: 길게 (ex: 30일) + DB에 해시 저장/회전(rotate) 권장
 *
 * Claims:
 *  - sub: kakaoUserId (String)
 *  - typ: "access" | "refresh"
 */
public class JwtProvider {

    private static final String CLAIM_TYP = "typ";
    private static final String TYP_ACCESS = "access";
    public static final String TYP_REFRESH = "refresh";

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;


    public JwtProvider(String secret, String issuer, int accessMinutes, int refreshDays) {
        Objects.requireNonNull(secret, "jwt secret must not be null");
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            // HS256에서 안전하게 쓰려면 32바이트 이상을 강력 권장
            throw new IllegalArgumentException("jwt secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = (issuer == null || issuer.isBlank()) ? "wm" : issuer;
        this.accessTtl = Duration.ofMinutes(accessMinutes);
        this.refreshTtl = Duration.ofDays(refreshDays);
    }

    public String createAccessToken(long kakaoUserId) {
        return createToken(kakaoUserId, TYP_ACCESS, accessTtl, Map.of());
    }

    public String createRefreshToken(long kakaoUserId) {
        // refresh는 추가로 "rot" 같은 토큰버전/nonce를 넣고 싶으면 여기서 claims에 넣으면 됨
        return createToken(kakaoUserId, TYP_REFRESH, refreshTtl, Map.of());
    }

    private String createToken(long kakaoUserId, String typ, Duration ttl, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);

        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(Long.toString(kakaoUserId))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .addClaims(extraClaims)
                .claim(CLAIM_TYP, typ)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isValidAccessToken(String token) {
        return isValidToken(token, TYP_ACCESS);
    }

    public boolean isValidRefreshToken(String token) {
        return isValidToken(token, TYP_REFRESH);
    }

    /* private boolean isValidToken(String token, String expectedTyp) {
        try {
            Claims claims = parseClaims(token);
            String typ = claims.get(CLAIM_TYP, String.class);
            return expectedTyp.equals(typ);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    } */

    private boolean isValidToken(String token, String expectedTyp) {
        try {
            Claims claims = parseClaims(token); // 여기서 서명/exp/iss 검증이 끝나야 함

            // 1) typ 체크
            String typ = claims.get(CLAIM_TYP, String.class);
            if (typ == null || !expectedTyp.equals(typ)) return false;

            // 2) subject 체크 (우리 구현에서 sub = kakaoUserId)
            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) return false;

            // 숫자 형식 보장 (kakaoUserId가 long이라고 했으니)
            Long.parseLong(sub);

            // 3) (선택) audience 체크를 쓰고 있다면
            // String aud = claims.getAudience();
            // if (!"wm-frontend".equals(aud)) return false;

            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    public Instant getExpiresAt(String token) {
        Claims claims = parseClaims(token);
        return claims.getExpiration().toInstant();
    }

    public boolean isExpired(String token) {
        try {
            parseClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    public String getTokenType(String token) {
        Claims claims = parseClaims(token);
        return claims.get(CLAIM_TYP, String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(issuer)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
}
