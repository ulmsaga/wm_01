package com.saga.wm.module.auth.controller;

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

import com.saga.wm.core.dto.ApiResponse;
import com.saga.wm.core.exception.ErrorCode;
import com.saga.wm.core.exception.WmException;
import com.saga.wm.core.util.RsaKeyUtil;
import com.saga.wm.module.auth.dto.LoginRequest;
import com.saga.wm.module.auth.jwt.JwtCookieUtil;
import com.saga.wm.module.auth.jwt.JwtProvider;
import com.saga.wm.module.auth.service.AuthService;
import com.saga.wm.module.auth.service.OtpService;
import com.saga.wm.module.auth.service.RefreshTokenService;

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

        long otpSeq = ((Number) body.get("otpSeq")).longValue();
        String otpCode = (String) body.get("otpCode");

        long userSeq = otpService.verifyOtp(otpSeq, otpCode);

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
            throw new WmException(ErrorCode.UNAUTHORIZED);
        }
        Map<String, Object> meData = authService.getMe(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok(meData));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, JwtCookieUtil.REFRESH_COOKIE);
        if (refreshToken == null) {
            throw new WmException(ErrorCode.UNAUTHORIZED);
        }

        try {
            JwtProvider.TokenPair newPair = refreshTokenService.refreshRotate(
                    refreshToken,
                    request.getHeader("User-Agent"),
                    request.getRemoteAddr());

            setTokenCookies(response, newPair);
            return ResponseEntity.ok(ApiResponse.ok(null));

        } catch (ExpiredJwtException e) {
            throw new WmException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalStateException e) {
            throw new WmException(ErrorCode.TOKEN_INVALID);
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
