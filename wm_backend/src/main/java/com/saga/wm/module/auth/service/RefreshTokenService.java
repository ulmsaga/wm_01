package com.saga.wm.module.auth.service;

import com.saga.wm.module.auth.jwt.JwtProvider.TokenPair;

public interface RefreshTokenService {

    TokenPair loginAndIssueTokens(long userSeq, String userAgent, String ipAddr);

    TokenPair refreshRotate(String refreshToken, String userAgent, String ipAddr);

    void logout(String refreshToken);
}