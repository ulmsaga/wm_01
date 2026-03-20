package com.mobigen.aiop.nttpoc.core.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 인증 (토큰)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),

    // 로그인
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED),
    ACCOUNT_INACTIVE(HttpStatus.UNAUTHORIZED),
    AUTH_LOCKED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS_FORMAT(HttpStatus.BAD_REQUEST),
    NO_SECOND_AUTH_TARGET(HttpStatus.BAD_REQUEST),

    // OTP
    OTP_NOT_FOUND(HttpStatus.UNAUTHORIZED),
    OTP_ALREADY_USED(HttpStatus.UNAUTHORIZED),
    OTP_EXPIRED(HttpStatus.UNAUTHORIZED),
    OTP_MAX_FAILED(HttpStatus.UNAUTHORIZED),
    OTP_MISMATCH(HttpStatus.UNAUTHORIZED),

    // 이메일 / SMS
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    SMS_NOT_SUPPORTED(HttpStatus.BAD_REQUEST),

    // 공통
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    /** enum 이름 → "error.{lower_case_dot}" 규칙으로 메시지 키 자동 파생 */
    public String getMessageKey() {
        return "error." + name().toLowerCase().replace('_', '.');
    }

    public HttpStatus getStatus() { return status; }
}
