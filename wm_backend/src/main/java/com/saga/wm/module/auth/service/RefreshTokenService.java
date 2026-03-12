package com.saga.wm.module.auth.service;

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

import io.jsonwebtoken.Claims;

@Service
public class RefreshTokenService {

    private final RefreshTokenDao refreshTokenDao;
    private final JwtProvider jwtProvider;

    public RefreshTokenService(RefreshTokenDao refreshTokenDao, JwtProvider jwtProvider) {
        this.refreshTokenDao = refreshTokenDao;
        this.jwtProvider = jwtProvider;
    }

    /** 로그인 성공 시: refresh jti_hash를 DB에 저장하고 access/refresh 발급 */
    @Transactional
    public TokenPair loginAndIssueTokens(long userId, String userAgent, String ipAddr) {
        TokenPair pair = jwtProvider.issueTokenPair(String.valueOf(userId), Map.of());

        // refresh 저장(해시)
        String refreshJtiHash = sha256Hex(pair.refreshJti());
        refreshTokenDao.insert(Map.of(
                "userId", userId,
                "jtiHash", refreshJtiHash,
                "issuedAt", java.sql.Timestamp.from(Instant.now()),
                "expiresAt", java.sql.Timestamp.from(pair.refreshExpiresAt()),
                "userAgent", userAgent,
                "ipAddr", ipAddr
        ));

        return pair;
    }

    /** refresh endpoint: refresh 토큰 검증 + rotate + 새 토큰 발급 */
    @Transactional
    public TokenPair refreshRotate(String refreshToken, String userAgent, String ipAddr) {
        Claims claims = jwtProvider.parseAndValidate(refreshToken, JwtProvider.TYP_REFRESH);
        long userId = Long.parseLong(jwtProvider.getSubject(claims));
        String oldJti = jwtProvider.getJti(claims);

        String oldJtiHash = sha256Hex(oldJti);
        Map<String, Object> row = refreshTokenDao.selectByJtiHash(Map.of("jtiHash", oldJtiHash));

        // 1) DB에 없으면 무조건 실패 (서버가 발급한 refresh가 아님)
        if (row == null) {
            throw new IllegalStateException("Refresh token not recognized");
        }

        // 2) revoked_at이 있으면: "재사용" 가능성이 큼(탈취/중복 사용)
        // 운영급에서는 보통 '전체 세션 폐기'까지 감
        Object revokedAt = row.get("revoked_at");
        if (revokedAt != null) {
            refreshTokenDao.revokeAllByUserId(Map.of("userId", userId));
            throw new IllegalStateException("Refresh token reuse detected -> revoked all sessions");
        }

        // 3) rotate: old 토큰 revoke + replaced_by 기록
        TokenPair newPair = jwtProvider.issueTokenPair(String.valueOf(userId), Map.of());
        String newJtiHash = sha256Hex(newPair.refreshJti());

        int updated = refreshTokenDao.rotate(Map.of("oldJtiHash", oldJtiHash, "newJtiHash", newJtiHash));
        if (updated != 1) {
            // 동시 요청 경쟁 조건 등 방어
            refreshTokenDao.revokeAllByUserId(Map.of("userId", userId));
            throw new IllegalStateException("Refresh rotation failed -> revoked all sessions");
        }

        // 4) 새 refresh 저장
        refreshTokenDao.insert(Map.of(
                "userId", userId,
                "jtiHash", newJtiHash,
                "issuedAt", java.sql.Timestamp.from(Instant.now()),
                "expiresAt", java.sql.Timestamp.from(newPair.refreshExpiresAt()),
                "userAgent", userAgent,
                "ipAddr", ipAddr
        ));

        return newPair;
    }

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