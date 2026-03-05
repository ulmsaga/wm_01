package com.saga.wm.module.auth.controller;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saga.wm.module.auth.jwt.JwtCookieUtil;
import com.saga.wm.module.auth.jwt.JwtProvider.TokenPair;
import com.saga.wm.module.auth.service.RefreshTokenService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RefreshTokenService refreshTokenService;
    private final JwtCookieUtil jwtCookieUtil;

    public AuthController(RefreshTokenService refreshTokenService, JwtCookieUtil jwtCookieUtil) {
        this.refreshTokenService = refreshTokenService;
        this.jwtCookieUtil = jwtCookieUtil;
    }

    // (임시) 로그인 흉내: uid를 userId로 보고 토큰 발급
    @PostMapping("/test-login")
    public ResponseEntity<?> testLogin(
            @RequestParam("uid") long uid,
            HttpServletRequest request
    ) {
        String ua = request.getHeader("User-Agent");
        String ip = request.getRemoteAddr();

        TokenPair pair = refreshTokenService.loginAndIssueTokens(uid, ua, ip);

        ResponseCookie access = jwtCookieUtil.accessCookie(pair.accessToken(), Duration.ofMinutes(30).getSeconds());
        ResponseCookie refresh = jwtCookieUtil.refreshCookie(pair.refreshToken(), Duration.ofDays(30).getSeconds());

        return ResponseEntity.ok()
                .header("Set-Cookie", access.toString())
                .header("Set-Cookie", refresh.toString())
                .body(Map.of(
                        "userId", uid,
                        "accessExpiresAt", pair.accessExpiresAt().toString(),
                        "refreshExpiresAt", pair.refreshExpiresAt().toString()
                ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        String refreshToken = readCookie(request, JwtCookieUtil.REFRESH_COOKIE);
        String ua = request.getHeader("User-Agent");
        String ip = request.getRemoteAddr();

        TokenPair pair = refreshTokenService.refreshRotate(refreshToken, ua, ip);

        ResponseCookie access = jwtCookieUtil.accessCookie(pair.accessToken(), Duration.ofMinutes(30).getSeconds());
        ResponseCookie refresh = jwtCookieUtil.refreshCookie(pair.refreshToken(), Duration.ofDays(30).getSeconds());

        return ResponseEntity.ok()
                .header("Set-Cookie", access.toString())
                .header("Set-Cookie", refresh.toString())
                .body(Map.of(
                        "refreshed", true,
                        "accessExpiresAt", pair.accessExpiresAt().toString(),
                        "refreshExpiresAt", pair.refreshExpiresAt().toString()
                ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String refreshToken = readCookie(request, JwtCookieUtil.REFRESH_COOKIE);
        refreshTokenService.logout(refreshToken);

        ResponseCookie delA = jwtCookieUtil.deleteAccessCookie();
        ResponseCookie delR = jwtCookieUtil.deleteRefreshCookie();

        return ResponseEntity.ok()
                .header("Set-Cookie", delA.toString())
                .header("Set-Cookie", delR.toString())
                .body(Map.of("logout", true));
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (var c : request.getCookies()) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}