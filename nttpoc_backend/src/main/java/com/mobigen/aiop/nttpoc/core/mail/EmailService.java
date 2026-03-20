package com.mobigen.aiop.nttpoc.core.mail;

public interface EmailService {

    /**
     * OTP 인증 코드 이메일 발송
     *
     * @param to            수신자 이메일 주소
     * @param otpCode       6자리 인증 코드
     * @param expireMinutes 유효 시간 (분)
     */
    void sendOtpEmail(String to, String otpCode, int expireMinutes);
}
