package com.saga.wm.module.auth.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saga.wm.core.exception.ErrorCode;
import com.saga.wm.core.exception.WmException;
import com.saga.wm.core.mail.EmailService;
import com.saga.wm.module.auth.dao.OtpDao;
import com.saga.wm.module.auth.service.OtpService;

@Service
public class OtpServiceImpl implements OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpServiceImpl.class);

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
    @Transactional(noRollbackFor = WmException.class)
    public long verifyOtp(long otpSeq, String otpCode) {
        Map<String, Object> otp = otpDao.selectOtpBySeq(otpSeq);
        if (otp == null) {
            throw new WmException(ErrorCode.OTP_NOT_FOUND);
        }

        // 이미 인증된 OTP
        if ("Y".equals(otp.get("VERIFIED_YN"))) {
            throw new WmException(ErrorCode.OTP_ALREADY_USED);
        }

        // 만료 확인
        LocalDateTime expiredAt = (LocalDateTime) otp.get("EXPIRED_AT");
        if (expiredAt.isBefore(LocalDateTime.now())) {
            throw new WmException(ErrorCode.OTP_EXPIRED);
        }

        // 실패 횟수 초과
        int failCount = ((Number) otp.get("FAIL_COUNT")).intValue();
        int maxFailCount = ((Number) otp.get("MAX_FAIL_COUNT")).intValue();
        if (failCount >= maxFailCount) {
            throw new WmException(ErrorCode.OTP_MAX_FAILED);
        }

        // 코드 검증
        String storedCode = (String) otp.get("OTP_CODE");
        if (!storedCode.equals(otpCode)) {
            otpDao.incrementOtpFailCount(otpSeq);
            int remaining = maxFailCount - failCount - 1;
            throw new WmException(ErrorCode.OTP_MISMATCH, remaining);
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
            log.debug("[OTP SMS 전송 - 미구현] type={}, target={}, code={}", sendType, sendTarget, otpCode);
        } else {
            log.warn("[OTP 전송] 알 수 없는 sendType={}", sendType);
        }
    }
}
