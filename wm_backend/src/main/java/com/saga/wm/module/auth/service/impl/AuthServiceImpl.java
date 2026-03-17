package com.saga.wm.module.auth.service.impl;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saga.wm.core.exception.ErrorCode;
import com.saga.wm.core.exception.WmException;
import com.saga.wm.core.util.PasswordUtil;
import com.saga.wm.core.util.RsaKeyUtil;
import com.saga.wm.module.auth.dao.AuthDao;
import com.saga.wm.module.auth.dto.LoginRequest;
import com.saga.wm.module.auth.jwt.JwtProvider;
import com.saga.wm.module.auth.service.AuthService;
import com.saga.wm.module.auth.service.OtpService;
import com.saga.wm.module.auth.service.RefreshTokenService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthDao authDao;
    private final RsaKeyUtil rsaKeyUtil;
    private final PasswordUtil passwordUtil;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AuthServiceImpl(
            AuthDao authDao,
            RsaKeyUtil rsaKeyUtil,
            PasswordUtil passwordUtil,
            RefreshTokenService refreshTokenService,
            OtpService otpService,
            ObjectMapper objectMapper) {
        this.authDao = authDao;
        this.rsaKeyUtil = rsaKeyUtil;
        this.passwordUtil = passwordUtil;
        this.refreshTokenService = refreshTokenService;
        this.otpService = otpService;
        this.objectMapper = objectMapper;
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
            throw new WmException(ErrorCode.INVALID_CREDENTIALS_FORMAT);
        }

        // 2. 사용자 + 인증수단 조회 (wm_user_auth JOIN wm_user)
        Map<String, Object> user = authDao.selectUserForLogin(
                Map.of("authType", "LOCAL", "authIdentifier", loginId));
        if (user == null) {
            throw new WmException(ErrorCode.LOGIN_FAILED);
        }

        // 계정/인증수단 상태 확인
        String userStatus = (String) user.get("USER_STATUS");
        String authStatus = (String) user.get("AUTH_STATUS");
        if (!"ACTIVE".equals(userStatus)) {
            throw new WmException(ErrorCode.ACCOUNT_INACTIVE);
        }
        if (!"ACTIVE".equals(authStatus)) {
            throw new WmException(ErrorCode.AUTH_LOCKED);
        }

        long userAuthSeq = ((Number) user.get("USER_AUTH_SEQ")).longValue();

        // 3. 비밀번호 검증 (LOCAL 인증 유형만)
        String storedHash = (String) user.get("PASSWORD_HASH");
        if (!passwordUtil.matches(password, storedHash)) {
            authDao.updateLoginFailure(Map.of("userAuthSeq", userAuthSeq));
            throw new WmException(ErrorCode.LOGIN_FAILED);
        }

        // 4. 로그인 성공 처리
        long userSeq = ((Number) user.get("USER_SEQ")).longValue();
        authDao.updateLoginSuccess(Map.of("userAuthSeq", userAuthSeq));
        authDao.updateUserLastLoginAt(Map.of("userSeq", userSeq));

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
                throw new WmException(ErrorCode.NO_SECOND_AUTH_TARGET);
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

        // 6. JWT 토큰 발급 (2차 인증 불필요 시)
        JwtProvider.TokenPair tokenPair = refreshTokenService.loginAndIssueTokens(
                userSeq, userAgent, ipAddr);

        // 7. 로그인 응답 생성
        Map<String, Object> loginResponse = Map.of(
                "userSeq", userSeq,
                "userUid", user.get("USER_UID"),
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
        return Map.of(
                "authenticated", true,
                "userSeq", ((Number) user.get("USER_SEQ")).longValue(),
                "userUid", user.get("USER_UID"),
                "userName", user.get("USER_NAME")
        );
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
