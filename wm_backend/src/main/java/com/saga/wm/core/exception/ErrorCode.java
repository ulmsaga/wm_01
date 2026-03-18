package com.saga.wm.core.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 인증 (토큰)
    UNAUTHORIZED("error.unauthorized", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("error.token.expired", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID("error.token.invalid", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("error.access.denied", HttpStatus.FORBIDDEN),

    // 로그인
    LOGIN_FAILED("error.login.failed", HttpStatus.UNAUTHORIZED),
    ACCOUNT_INACTIVE("error.account.inactive", HttpStatus.UNAUTHORIZED),
    AUTH_LOCKED("error.auth.locked", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS_FORMAT("error.invalid.credentials.format", HttpStatus.BAD_REQUEST),
    NO_SECOND_AUTH_TARGET("error.no.second.auth.target", HttpStatus.BAD_REQUEST),

    // OTP
    OTP_NOT_FOUND("error.otp.not.found", HttpStatus.UNAUTHORIZED),
    OTP_ALREADY_USED("error.otp.already.used", HttpStatus.UNAUTHORIZED),
    OTP_EXPIRED("error.otp.expired", HttpStatus.UNAUTHORIZED),
    OTP_MAX_FAILED("error.otp.max.failed", HttpStatus.UNAUTHORIZED),
    OTP_MISMATCH("error.otp.mismatch", HttpStatus.UNAUTHORIZED),

    // 이메일
    EMAIL_SEND_FAILED("error.email.send.failed", HttpStatus.INTERNAL_SERVER_ERROR),

    // 공통
    BAD_REQUEST("error.bad.request", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("error.internal.server.error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String messageKey;
    private final HttpStatus status;

    ErrorCode(String messageKey, HttpStatus status) {
        this.messageKey = messageKey;
        this.status = status;
    }

    public String getMessageKey() { return messageKey; }
    public HttpStatus getStatus() { return status; }
}
