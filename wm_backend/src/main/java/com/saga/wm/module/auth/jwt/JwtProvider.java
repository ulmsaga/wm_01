package com.saga.wm.module.auth.jwt;

import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtProvider {

    public static final String CLAIM_TYP = "typ";   // access | refresh
    public static final String CLAIM_JTI = "jti";   // UUID string
    public static final String TYP_ACCESS = "access";
    public static final String TYP_REFRESH = "refresh";

    private final String issuer;
    private final Key key;

    private final long accessMinutes;
    private final long refreshDays;

    public JwtProvider(
            @Value("${app.jwt.issuer:wm}") String issuer,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.accessMinutes:30}") long accessMinutes,
            @Value("${app.jwt.refreshDays:30}") long refreshDays
    ) {
        this.issuer = issuer;
        this.accessMinutes = accessMinutes;
        this.refreshDays = refreshDays;

        // HS256
        // byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = Decoders.BASE64.decode(secret);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }

        // this.key = new SecretKeySpec(secretBytes, SignatureAlgorithm.HS256.getJcaName());
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public TokenPair issueTokenPair(String userId, Map<String, Object> extraClaims) {
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        String access = createToken(userId, TYP_ACCESS, accessJti, accessMinutes, ChronoUnit.MINUTES, extraClaims);
        String refresh = createToken(userId, TYP_REFRESH, refreshJti, refreshDays, ChronoUnit.DAYS, Map.of());

        return new TokenPair(access, refresh, accessJti, refreshJti,
                Instant.now().plus(accessMinutes, ChronoUnit.MINUTES),
                Instant.now().plus(refreshDays, ChronoUnit.DAYS));
    }

    private String createToken(
            String sub,
            String typ,
            String jti,
            long amount,
            ChronoUnit unit,
            Map<String, Object> extraClaims
    ) {
        Instant now = Instant.now();
        Instant exp = now.plus(amount, unit);

        Claims claims = Jwts.claims()
                .setIssuer(issuer)
                .setSubject(sub)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp));

        claims.put(CLAIM_TYP, typ);
        claims.put(CLAIM_JTI, jti);

        if (extraClaims != null) {
            extraClaims.forEach((k, v) -> {
                if (v != null) claims.put(k, v);
            });
        }

        return Jwts.builder()
                .setClaims(claims)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseAndValidate(String token, String expectedTyp) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .requireIssuer(issuer)
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String typ = claims.get(CLAIM_TYP, String.class);
            if (!expectedTyp.equals(typ)) {
                throw new JwtException("Invalid token typ");
            }
            // exp/iat/iss/signature는 parser가 이미 검증
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            throw e;
        }
    }

    public String getSubject(Claims claims) {
        return claims.getSubject();
    }

    public String getJti(Claims claims) {
        return claims.get(CLAIM_JTI, String.class);
    }

    public record TokenPair(
            String accessToken,
            String refreshToken,
            String accessJti,
            String refreshJti,
            Instant accessExpiresAt,
            Instant refreshExpiresAt
    ) {}
}