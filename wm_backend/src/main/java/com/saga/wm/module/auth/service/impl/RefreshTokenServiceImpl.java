package com.saga.wm.module.auth.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saga.wm.module.auth.dao.RefreshTokenDao;
import com.saga.wm.module.auth.jwt.JwtProvider;
import com.saga.wm.module.auth.jwt.JwtProvider.TokenPair;
import com.saga.wm.module.auth.service.RefreshTokenService;

import io.jsonwebtoken.Claims;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenDao refreshTokenDao;
    private final JwtProvider jwtProvider;

    public RefreshTokenServiceImpl(RefreshTokenDao refreshTokenDao, JwtProvider jwtProvider) {
        this.refreshTokenDao = refreshTokenDao;
        this.jwtProvider = jwtProvider;
    }

    @Override
    @Transactional
    public TokenPair loginAndIssueTokens(long userSeq, String userAgent, String ipAddr) {
        TokenPair pair = jwtProvider.issueTokenPair(String.valueOf(userSeq), Map.of());

        String refreshJtiHash = sha256Hex(pair.refreshJti());
        refreshTokenDao.insert(Map.of(
                "userSeq", userSeq,
                "jtiHash", refreshJtiHash,
                "issuedAt", java.sql.Timestamp.from(Instant.now()),
                "expiresAt", java.sql.Timestamp.from(pair.refreshExpiresAt()),
                "userAgent", userAgent,
                "ipAddr", ipAddr
        ));

        return pair;
    }

    @Override
    @Transactional
    public TokenPair refreshRotate(String refreshToken, String userAgent, String ipAddr) {
        Claims claims = jwtProvider.parseAndValidate(refreshToken, JwtProvider.TYP_REFRESH);
        long userSeq = Long.parseLong(jwtProvider.getSubject(claims));
        String oldJti = jwtProvider.getJti(claims);

        String oldJtiHash = sha256Hex(oldJti);
        Map<String, Object> row = refreshTokenDao.selectByJtiHash(Map.of("jtiHash", oldJtiHash));

        if (row == null) {
            throw new IllegalStateException("Refresh token not recognized");
        }

        Object revokedAt = row.get("revoked_at");
        if (revokedAt != null) {
            refreshTokenDao.revokeAllByUserSeq(Map.of("userSeq", userSeq));
            throw new IllegalStateException("Refresh token reuse detected -> revoked all sessions");
        }

        TokenPair newPair = jwtProvider.issueTokenPair(String.valueOf(userSeq), Map.of());
        String newJtiHash = sha256Hex(newPair.refreshJti());

        int updated = refreshTokenDao.rotate(Map.of("oldJtiHash", oldJtiHash, "newJtiHash", newJtiHash));
        if (updated != 1) {
            refreshTokenDao.revokeAllByUserSeq(Map.of("userSeq", userSeq));
            throw new IllegalStateException("Refresh rotation failed -> revoked all sessions");
        }

        refreshTokenDao.insert(Map.of(
                "userSeq", userSeq,
                "jtiHash", newJtiHash,
                "issuedAt", java.sql.Timestamp.from(Instant.now()),
                "expiresAt", java.sql.Timestamp.from(newPair.refreshExpiresAt()),
                "userAgent", userAgent,
                "ipAddr", ipAddr
        ));

        return newPair;
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;

        Claims claims = jwtProvider.parseAndValidate(refreshToken, JwtProvider.TYP_REFRESH);
        String jti = jwtProvider.getJti(claims);
        refreshTokenDao.revokeByJtiHash(Map.of("jtiHash", sha256Hex(jti)));
    }

    @SuppressWarnings("UseSpecificCatch")
    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
