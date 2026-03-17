package com.saga.wm.module.auth.service;

public interface OtpService {

    /**
     * OTP 생성 및 전송 (EMAIL / SMS).
     * @return 생성된 otp_seq
     */
    long createAndSendOtp(long userSeq, String sendType, String sendTarget);

    /**
     * OTP 검증.
     * 성공 시 해당 OTP 레코드의 user_seq 반환.
     * 실패 시 예외 발생.
     */
    long verifyOtp(long otpSeq, String otpCode);
}
