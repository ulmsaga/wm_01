package com.mobigen.aiop.nttpoc.module.auth.service;

import com.mobigen.aiop.nttpoc.module.auth.jwt.JwtProvider.TokenPair;

public interface RefreshTokenService {

    TokenPair loginAndIssueTokens(long userSeq, String userAgent, String ipAddr);

    TokenPair refreshRotate(String refreshToken, String userAgent, String ipAddr);

    void logout(String refreshToken);

    /** 해당 사용자의 모든 활성 RT를 revoke (중복 로그인 처리용). revoke된 건수 반환. */
    int revokeAllSessions(long userSeq);

    /** 해당 사용자의 활성 세션(RT) 존재 여부 확인. */
    boolean hasActiveSessions(long userSeq);
}
