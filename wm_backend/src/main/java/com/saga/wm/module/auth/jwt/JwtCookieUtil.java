package com.saga.wm.module.auth.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class JwtCookieUtil {

    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";

    private final boolean secure;
    private final String sameSite;

    public JwtCookieUtil(
            @Value("${app.cookie.secure:false}") boolean secure,
            @Value("${app.cookie.sameSite:Lax}") String sameSite
    ) {
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public ResponseCookie accessCookie(String token, long maxAgeSeconds) {
        return baseCookie(ACCESS_COOKIE, token, maxAgeSeconds);
    }

    public ResponseCookie refreshCookie(String token, long maxAgeSeconds) {
        return baseCookie(REFRESH_COOKIE, token, maxAgeSeconds);
    }

    public ResponseCookie deleteAccessCookie() {
        return baseCookie(ACCESS_COOKIE, "", 0);
    }

    public ResponseCookie deleteRefreshCookie() {
        return baseCookie(REFRESH_COOKIE, "", 0);
    }

    private ResponseCookie baseCookie(String name, String value, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .path("/")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .maxAge(maxAgeSeconds)
                .build();
    }
}