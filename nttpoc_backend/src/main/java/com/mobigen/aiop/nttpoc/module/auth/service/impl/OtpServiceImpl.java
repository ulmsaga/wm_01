package com.mobigen.aiop.nttpoc.module.auth.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mobigen.aiop.nttpoc.core.exception.ErrorCode;
import com.mobigen.aiop.nttpoc.core.exception.NttpocException;
import com.mobigen.aiop.nttpoc.core.mail.EmailService;
import com.mobigen.aiop.nttpoc.module.auth.dao.OtpDao;
import com.mobigen.aiop.nttpoc.module.auth.service.OtpService;

@Service
public class OtpServiceImpl implements OtpService {

    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRE_MINUTES = 5;
    private static final int MAX_FAIL_COUNT = 3;

    private final OtpDao otpDao;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public OtpServiceImpl(OtpDao otpDao, EmailService emailService) {
        this.otpDao = otpDao;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public long createAndSendOtp(long userSeq, String sendType, String sendTarget) {
        // 1. 6자리 OTP 생성
        String otpCode = generateOtpCode();

        // 2. DB 저장
        Map<String, Object> param = new HashMap<>();
        param.put("userSeq", userSeq);
        param.put("otpCode", otpCode);
        param.put("otpType", "LOGIN_2FA");
        param.put("sendType", sendType);
        param.put("sendTarget", sendTarget);
        param.put("maxFailCount", MAX_FAIL_COUNT);
        param.put("expiredAt", LocalDateTime.now().plusMinutes(OTP_EXPIRE_MINUTES));

        otpDao.insertOtp(param);
        long otpSeq = ((Number) param.get("otpSeq")).longValue();

        // 3. OTP 전송 (추후 모듈화 예정)
        sendOtp(sendType, sendTarget, otpCode);

        return otpSeq;
    }

    @Override
    @Transactional(noRollbackFor = NttpocException.class)
    public long verifyOtp(long otpSeq, String otpCode) {
        Map<String, Object> otp = otpDao.selectOtpBySeq(otpSeq);
        if (otp == null) {
            throw new NttpocException(ErrorCode.OTP_NOT_FOUND);
        }

        // 이미 인증된 OTP
        if ("Y".equals(otp.get("VERIFIED_YN"))) {
            throw new NttpocException(ErrorCode.OTP_ALREADY_USED);
        }

        // 만료 확인
        LocalDateTime expiredAt = (LocalDateTime) otp.get("EXPIRED_AT");
        if (expiredAt.isBefore(LocalDateTime.now())) {
            throw new NttpocException(ErrorCode.OTP_EXPIRED);
        }

        // 실패 횟수 초과
        int failCount = ((Number) otp.get("FAIL_COUNT")).intValue();
        int maxFailCount = ((Number) otp.get("MAX_FAIL_COUNT")).intValue();
        if (failCount >= maxFailCount) {
            throw new NttpocException(ErrorCode.OTP_MAX_FAILED);
        }

        // 코드 검증
        String storedCode = (String) otp.get("OTP_CODE");
        if (!storedCode.equals(otpCode)) {
            otpDao.incrementOtpFailCount(otpSeq);
            int remaining = maxFailCount - failCount - 1;
            throw new NttpocException(ErrorCode.OTP_MISMATCH, remaining);
        }

        // 인증 성공
        otpDao.updateOtpVerified(otpSeq);
        return ((Number) otp.get("USER_SEQ")).longValue();
    }

    private String generateOtpCode() {
        int bound = (int) Math.pow(10, OTP_LENGTH);
        int code = secureRandom.nextInt(bound);
        return String.format("%0" + OTP_LENGTH + "d", code);
    }

    @SuppressWarnings("ConvertToStringSwitch")
    private void sendOtp(String sendType, String sendTarget, String otpCode) {
        if ("EMAIL".equals(sendType)) {
            emailService.sendOtpEmail(sendTarget, otpCode, OTP_EXPIRE_MINUTES);
        } else if ("SMS".equals(sendType)) {
            throw new NttpocException(ErrorCode.SMS_NOT_SUPPORTED);
        } else {
            throw new NttpocException(ErrorCode.SMS_NOT_SUPPORTED);
        }
    }
}
