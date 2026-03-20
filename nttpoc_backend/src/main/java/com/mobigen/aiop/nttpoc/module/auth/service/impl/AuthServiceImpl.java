package com.mobigen.aiop.nttpoc.module.auth.service.impl;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mobigen.aiop.nttpoc.core.exception.ErrorCode;
import com.mobigen.aiop.nttpoc.core.exception.NttpocException;
import com.mobigen.aiop.nttpoc.core.util.PasswordUtil;
import com.mobigen.aiop.nttpoc.core.util.RsaKeyUtil;
import com.mobigen.aiop.nttpoc.module.auth.dao.AuthDao;
import com.mobigen.aiop.nttpoc.module.auth.dto.LoginRequest;
import com.mobigen.aiop.nttpoc.module.auth.jwt.JwtProvider;
import com.mobigen.aiop.nttpoc.module.auth.service.AuthService;
import com.mobigen.aiop.nttpoc.module.auth.service.OtpService;
import com.mobigen.aiop.nttpoc.module.auth.service.RefreshTokenService;
import com.mobigen.aiop.nttpoc.module.auth.sse.SseSessionRegistry;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuthServiceImpl implements AuthService {

    private static final int MAX_LOGIN_FAIL_COUNT = 5;

    private final AuthDao authDao;
    private final RsaKeyUtil rsaKeyUtil;
    private final PasswordUtil passwordUtil;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;
    private final ObjectMapper objectMapper;
    private final SseSessionRegistry sseSessionRegistry;
    private final MessageSource messageSource;

    @Autowired
    public AuthServiceImpl(
            AuthDao authDao,
            RsaKeyUtil rsaKeyUtil,
            PasswordUtil passwordUtil,
            RefreshTokenService refreshTokenService,
            OtpService otpService,
            ObjectMapper objectMapper,
            SseSessionRegistry sseSessionRegistry,
            MessageSource messageSource) {
        this.authDao = authDao;
        this.rsaKeyUtil = rsaKeyUtil;
        this.passwordUtil = passwordUtil;
        this.refreshTokenService = refreshTokenService;
        this.otpService = otpService;
        this.objectMapper = objectMapper;
        this.sseSessionRegistry = sseSessionRegistry;
        this.messageSource = messageSource;
    }

    @Override
    @Transactional
    public LoginResult login(LoginRequest request, String userAgent, String ipAddr) {
        // 1. RSA 복호화
        String decryptedJson = rsaKeyUtil.decrypt(request.getKeyId(), request.getEncryptedCredentials());

        String loginId;
        String password;
        try {
            JsonNode node = objectMapper.readTree(decryptedJson);
            loginId = node.get("loginId").asText();
            password = node.get("password").asText();
        } catch (JacksonException e) {
            throw new NttpocException(ErrorCode.INVALID_CREDENTIALS_FORMAT);
        }

        // 2. 사용자 + 인증수단 조회 (wm_user_auth JOIN wm_user)
        Map<String, Object> user = authDao.selectUserForLogin(
                Map.of("authType", "LOCAL", "authIdentifier", loginId));
        if (user == null) {
            throw new NttpocException(ErrorCode.LOGIN_FAILED);
        }

        // 계정/인증수단 상태 확인
        String userStatus = (String) user.get("USER_STATUS");
        String authStatus = (String) user.get("AUTH_STATUS");
        if (!"ACTIVE".equals(userStatus)) {
            throw new NttpocException(ErrorCode.ACCOUNT_INACTIVE);
        }
        if (!"ACTIVE".equals(authStatus)) {
            throw new NttpocException(ErrorCode.AUTH_LOCKED);
        }

        long userAuthSeq = ((Number) user.get("USER_AUTH_SEQ")).longValue();

        // 3. 비밀번호 검증 (LOCAL 인증 유형만)
        String storedHash = (String) user.get("PASSWORD_HASH");
        if (!passwordUtil.matches(password, storedHash)) {
            authDao.updateLoginFailure(Map.of("userAuthSeq", userAuthSeq));
            int failedCount = ((Number) user.get("FAILED_LOGIN_COUNT")).intValue() + 1;
            if (failedCount >= MAX_LOGIN_FAIL_COUNT) {
                throw new NttpocException(ErrorCode.AUTH_LOCKED);
            }
            throw new NttpocException(ErrorCode.LOGIN_FAILED);
        }

        // 4. 로그인 성공 처리
        long userSeq = ((Number) user.get("USER_SEQ")).longValue();
        authDao.updateLoginSuccess(Map.of("userAuthSeq", userAuthSeq));
        authDao.updateUserLastLoginAt(Map.of("userSeq", userSeq));

        // 4-1. 중복 로그인 불허(N) + 기존 세션 있음 + forceLogin 아님 → 프론트 확인 요청
        String allowDuplicateLogin = (String) user.get("ALLOW_DUPLICATE_LOGIN");
        if ("N".equals(allowDuplicateLogin)
                && !request.isForceLogin()
                && refreshTokenService.hasActiveSessions(userSeq)) {
            String confirmMsg = messageSource.getMessage(
                    "msg.duplicate.login.confirm", null, LocaleContextHolder.getLocale());
            return new LoginResult(
                    Map.of("requireDuplicateConfirm", true, "message", confirmMsg),
                    null);
        }

        // 5. 2차 인증 필요 여부 확인
        String requireSecondAuth = (String) user.get("REQUIRE_SECOND_AUTH");
        if ("Y".equals(requireSecondAuth)) {
            // 이메일 우선, 없으면 SMS
            String email = (String) user.get("EMAIL");
            String phone = (String) user.get("PHONE");

            String sendType;
            String sendTarget;
            if (email != null && !email.isBlank()) {
                sendType = "EMAIL";
                sendTarget = email;
            } else if (phone != null && !phone.isBlank()) {
                sendType = "SMS";
                sendTarget = phone;
            } else {
                throw new NttpocException(ErrorCode.NO_SECOND_AUTH_TARGET);
            }

            long otpSeq = otpService.createAndSendOtp(userSeq, sendType, sendTarget);

            Map<String, Object> otpResponse = Map.of(
                    "requireSecondAuth", true,
                    "otpSeq", otpSeq,
                    "sendType", sendType,
                    "sendTarget", maskTarget(sendType, sendTarget)
            );
            return new LoginResult(otpResponse, null);
        }

        // 6. JWT 토큰 발급 (2차 인증 불필요 시) — 발급 전 중복 로그인 처리
        doKickIfDuplicateNotAllowed(userSeq, (String) user.get("ALLOW_DUPLICATE_LOGIN"));
        JwtProvider.TokenPair tokenPair = refreshTokenService.loginAndIssueTokens(
                userSeq, userAgent, ipAddr);

        // 7. 로그인 응답 생성
        Map<String, Object> loginResponse = Map.of(
                "userSeq", userSeq,
                
                "userName", user.get("USER_NAME")
        );

        return new LoginResult(loginResponse, tokenPair);
    }

    @Override
    public Map<String, Object> getMe(String userSeqStr) {
        long userSeq = Long.parseLong(userSeqStr);
        Map<String, Object> user = authDao.selectUserInfoByUserSeq(userSeq);
        if (user == null) {
            return Map.of("authenticated", false);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("authenticated", true);
        result.put("userSeq", ((Number) user.get("USER_SEQ")).longValue());
        result.put("userId", user.get("USER_ID"));
        result.put("userName", user.get("USER_NAME"));
        return result;
    }

    /** 2FA 경로 등 외부에서 호출 — DB에서 allow_duplicate_login 조회 후 처리 */
    @Override
    public void kickIfDuplicateNotAllowed(long userSeq) {
        String allowDup = authDao.selectAllowDuplicateLogin(userSeq);
        doKickIfDuplicateNotAllowed(userSeq, allowDup);
    }

    /** 로그인 흐름 내부에서 호출 — 이미 조회된 값 재사용 */
    private void doKickIfDuplicateNotAllowed(long userSeq, String allowDuplicateLogin) {
        if (!"N".equals(allowDuplicateLogin)) return;
        int revoked = refreshTokenService.revokeAllSessions(userSeq);
        if (revoked == 0) return; // 기존 세션 없음 → 최초 로그인이거나 이미 만료됨
        String userId = String.valueOf(userSeq);
        sseSessionRegistry.markRevoked(userId);
        sseSessionRegistry.send(userId, "SESSION_INVALIDATED", "duplicate_login");
    }

    /**
     * 전송 대상을 마스킹하여 프론트에 노출.
     * EMAIL: ab***@domain.com / SMS: 010****5678
     */
    private String maskTarget(String sendType, String target) {
        if ("EMAIL".equals(sendType) && target.contains("@")) {
            String[] parts = target.split("@");
            String local = parts[0];
            String masked = local.length() <= 2
                    ? local + "***"
                    : local.substring(0, 2) + "***";
            return masked + "@" + parts[1];
        }
        // SMS
        if (target.length() >= 7) {
            return target.substring(0, 3)
                    + "****"
                    + target.substring(target.length() - 4);
        }
        return "****";
    }
}
