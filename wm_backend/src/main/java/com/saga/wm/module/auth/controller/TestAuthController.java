package com.saga.wm.module.auth.controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saga.wm.module.auth.jwt.JwtCookieUtil;
import com.saga.wm.module.auth.jwt.JwtProvider;

@RestController
@RequestMapping("/api/auth")
public class TestAuthController {

    private final JwtProvider jwtProvider;
    private final JwtCookieUtil jwtCookieUtil;
    private final Duration accessTtl;

    public TestAuthController(
            JwtProvider jwtProvider,
            JwtCookieUtil jwtCookieUtil,
            @Qualifier("accessTtl") Duration accessTtl) {
        this.jwtProvider = jwtProvider;
        this.jwtCookieUtil = jwtCookieUtil;
        this.accessTtl = accessTtl;
    }

    // 예: /api/auth/test-login?uid=12345
    @GetMapping("/test-login")
    public ResponseEntity<?> testLogin(@RequestParam(defaultValue = "12345") long uid) {
        String access = jwtProvider.createAccessToken(uid);

        HttpHeaders headers = new HttpHeaders();
        jwtCookieUtil.addAccessCookie(headers, access, accessTtl);

        return ResponseEntity.ok()
                .headers(headers)
                .body(java.util.Map.of("uid", uid, "message", "access cookie set"));
    }
}