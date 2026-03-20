package com.mobigen.aiop.nttpoc.module.auth.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mobigen.aiop.nttpoc.core.dto.ApiResponse;
import com.mobigen.aiop.nttpoc.core.exception.ErrorCode;
import com.mobigen.aiop.nttpoc.core.exception.NttpocException;
import com.mobigen.aiop.nttpoc.core.util.RsaKeyUtil;
import com.mobigen.aiop.nttpoc.module.auth.dto.LoginRequest;
import com.mobigen.aiop.nttpoc.module.auth.jwt.JwtCookieUtil;
import com.mobigen.aiop.nttpoc.module.auth.jwt.JwtProvider;
import com.mobigen.aiop.nttpoc.module.auth.service.AuthService;
import com.mobigen.aiop.nttpoc.module.auth.service.OtpService;
import com.mobigen.aiop.nttpoc.module.auth.service.RefreshTokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;
    private final RsaKeyUtil rsaKeyUtil;
    private final JwtCookieUtil jwtCookieUtil;

    @Autowired
    public AuthController(
            AuthService authService,
            RefreshTokenService refreshTokenService,
            OtpService otpService,
            RsaKeyUtil rsaKeyUtil,
            JwtCookieUtil jwtCookieUtil) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.otpService = otpService;
        this.rsaKeyUtil = rsaKeyUtil;
        this.jwtCookieUtil = jwtCookieUtil;
    }

    /** RSA 공개키 발급 (일회용, TTL 5분) */
    @GetMapping("/rsa-public-key")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRsaPublicKey() {
        RsaKeyUtil.RsaPublicKeyInfo keyInfo = rsaKeyUtil.generateKeyPair();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "keyId", keyInfo.keyId(),
                "publicKey", keyInfo.publicKeyBase64()
        )));
    }

    /** 1차 인증: RSA 암호화된 ID/PW로 로그인 */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddr = httpRequest.getRemoteAddr();

        AuthService.LoginResult result = authService.login(request, userAgent, ipAddr);

        // 2차 인증 필요 시 — JWT 발급 없이 OTP 정보만 반환
        if (result.tokenPair() == null) {
            return ResponseEntity.ok(ApiResponse.ok(result.response()));
        }

        setTokenCookies(httpResponse, result.tokenPair());
        return ResponseEntity.ok(ApiResponse.ok(result.response()));
    }

    /** 2차 인증: OTP 검증 후 JWT 발급 */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyOtp(
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        Object otpSeqRaw = body.get("otpSeq");
        Object otpCodeRaw = body.get("otpCode");
        if (otpSeqRaw == null || otpCodeRaw == null) {
            throw new NttpocException(ErrorCode.BAD_REQUEST);
        }
        long otpSeq = ((Number) otpSeqRaw).longValue();
        String otpCode = (String) otpCodeRaw;

        long userSeq = otpService.verifyOtp(otpSeq, otpCode);

        // 2FA 완료 시점에 중복 로그인 처리 (allow_duplicate_login = N이면 기존 세션 kick)
        authService.kickIfDuplicateNotAllowed(userSeq);

        JwtProvider.TokenPair tokenPair = refreshTokenService.loginAndIssueTokens(
                userSeq, httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr());

        setTokenCookies(httpResponse, tokenPair);

        Map<String, Object> meData = authService.getMe(String.valueOf(userSeq));
        return ResponseEntity.ok(ApiResponse.ok(meData));
    }

    /** 현재 인증된 사용자 정보 조회 */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new NttpocException(ErrorCode.UNAUTHORIZED);
        }
        Map<String, Object> meData = authService.getMe(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok(meData));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, JwtCookieUtil.REFRESH_COOKIE);
        if (refreshToken == null) {
            throw new NttpocException(ErrorCode.UNAUTHORIZED);
        }

        try {
            JwtProvider.TokenPair newPair = refreshTokenService.refreshRotate(
                    refreshToken,
                    request.getHeader("User-Agent"),
                    request.getRemoteAddr());

            setTokenCookies(response, newPair);
            return ResponseEntity.ok(ApiResponse.ok(null));

        } catch (ExpiredJwtException e) {
            throw new NttpocException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException e) {
            throw new NttpocException(ErrorCode.TOKEN_INVALID);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, JwtCookieUtil.REFRESH_COOKIE);
        refreshTokenService.logout(refreshToken);

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookieUtil.deleteAccessCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookieUtil.deleteRefreshCookie().toString());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (jakarta.servlet.http.Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private void setTokenCookies(HttpServletResponse response, JwtProvider.TokenPair tokenPair) {
        long accessMaxAge = Duration.between(Instant.now(), tokenPair.accessExpiresAt()).toSeconds();
        long refreshMaxAge = Duration.between(Instant.now(), tokenPair.refreshExpiresAt()).toSeconds();
        response.addHeader(HttpHeaders.SET_COOKIE,
                jwtCookieUtil.accessCookie(tokenPair.accessToken(), accessMaxAge).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                jwtCookieUtil.refreshCookie(tokenPair.refreshToken(), refreshMaxAge).toString());
    }
}
