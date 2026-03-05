package com.saga.wm.module.auth.jwt;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;

/**
 * HttpOnly 쿠키 생성/삭제 유틸
 *
 * - Access 쿠키: 짧게
 * - Refresh 쿠키: 길게
 *
 * SameSite:
 *  - 보통 "Lax" 권장 (대부분 안전 + 일반 로그인 플로우 잘 동작)
 *  - 프론트/백엔드 도메인이 완전히 분리되고, 특정 크로스사이트 요구가 있으면 "None"+Secure 필요
 */
public class JwtCookieUtil {

    public static final String COOKIE_ACCESS = "access_token";
    public static final String COOKIE_REFRESH = "refresh_token";

    private final boolean secure;
    private final String sameSite;
    private final String cookiePath;

    /**
     * @param secure   운영(https)이면 true, 로컬(http)이면 false
     * @param sameSite "Lax" 권장. (필요 시 "None" + secure=true)
     * @param cookiePath 보통 "/"
     */
    public JwtCookieUtil(boolean secure, String sameSite, String cookiePath) {
        this.secure = secure;
        this.sameSite = StringUtils.hasText(sameSite) ? sameSite : "Lax";
        this.cookiePath = StringUtils.hasText(cookiePath) ? cookiePath : "/";
    }

    public String buildAccessSetCookieHeader(String accessToken, Duration accessTtl) {
        return buildSetCookie(COOKIE_ACCESS, accessToken, accessTtl).toString();
    }

    public String buildRefreshSetCookieHeader(String refreshToken, Duration refreshTtl) {
        return buildSetCookie(COOKIE_REFRESH, refreshToken, refreshTtl).toString();
    }

    public String buildAccessDeleteCookieHeader() {
        return buildDeleteCookie(COOKIE_ACCESS).toString();
    }

    public String buildRefreshDeleteCookieHeader() {
        return buildDeleteCookie(COOKIE_REFRESH).toString();
    }

    public void addAccessCookie(HttpHeaders headers, String accessToken, Duration accessTtl) {
        headers.add(HttpHeaders.SET_COOKIE, buildAccessSetCookieHeader(accessToken, accessTtl));
    }

    public void addRefreshCookie(HttpHeaders headers, String refreshToken, Duration refreshTtl) {
        headers.add(HttpHeaders.SET_COOKIE, buildRefreshSetCookieHeader(refreshToken, refreshTtl));
    }

    public void addDeleteCookies(HttpHeaders headers) {
        headers.add(HttpHeaders.SET_COOKIE, buildAccessDeleteCookieHeader());
        headers.add(HttpHeaders.SET_COOKIE, buildRefreshDeleteCookieHeader());
    }

    private ResponseCookie buildSetCookie(String name, String value, Duration ttl) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path(cookiePath)
                .sameSite(sameSite)
                .maxAge(ttl)
                .build();
    }

    private ResponseCookie buildDeleteCookie(String name) {
        // maxAge=0 => 즉시 만료
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secure)
                .path(cookiePath)
                .sameSite(sameSite)
                .maxAge(0)
                .build();
    }
}