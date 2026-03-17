package com.saga.wm.module.auth.service;

import java.util.Map;

import com.saga.wm.module.auth.dto.LoginRequest;
import com.saga.wm.module.auth.jwt.JwtProvider;

public interface AuthService {

    LoginResult login(LoginRequest request, String userAgent, String ipAddr);

    Map<String, Object> getMe(String userId);

    record LoginResult(Map<String, Object> response, JwtProvider.TokenPair tokenPair) {}
}
