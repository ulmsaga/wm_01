package com.saga.wm.module.auth.service;

import java.util.Map;

import com.saga.wm.module.auth.dto.LoginRequest;
import com.saga.wm.module.auth.jwt.JwtProvider;

public interface AuthService {

    LoginResult login(LoginRequest request, String userAgent, String ipAddr);

    Map<String, Object> getMe(String userId);

    /**
     * allow_duplicate_login = N 사용자가 재로그인 시
     * 기존 세션(RT) 전체 revoke + SSE SESSION_INVALIDATED push.
     * JWT 발급 직전(non-2FA: login(), 2FA: verifyOtp())에 호출.
     */
    void kickIfDuplicateNotAllowed(long userSeq);

    record LoginResult(Map<String, Object> response, JwtProvider.TokenPair tokenPair) {}
}
