package com.saga.wm.core.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 인증 (토큰)
    UNAUTHORIZED("인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("토큰이 만료되었습니다. 다시 로그인해 주세요.", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID("유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("접근 권한이 없습니다.", HttpStatus.FORBIDDEN),

    // 로그인
    LOGIN_FAILED("아이디 또는 비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
    ACCOUNT_INACTIVE("비활성화된 계정입니다.", HttpStatus.UNAUTHORIZED),
    AUTH_LOCKED("잠금된 인증수단입니다.", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS_FORMAT("잘못된 인증 데이터 형식입니다.", HttpStatus.BAD_REQUEST),
    NO_SECOND_AUTH_TARGET("2차 인증 수단(이메일/전화번호)이 등록되어 있지 않습니다.", HttpStatus.BAD_REQUEST),

    // OTP
    OTP_NOT_FOUND("유효하지 않은 OTP 요청입니다.", HttpStatus.UNAUTHORIZED),
    OTP_ALREADY_USED("이미 사용된 OTP입니다.", HttpStatus.UNAUTHORIZED),
    OTP_EXPIRED("OTP가 만료되었습니다. 다시 로그인해 주세요.", HttpStatus.UNAUTHORIZED),
    OTP_MAX_FAILED("OTP 인증 시도 횟수를 초과했습니다. 다시 로그인해 주세요.", HttpStatus.UNAUTHORIZED),
    OTP_MISMATCH("OTP가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),

    // 이메일
    EMAIL_SEND_FAILED("이메일 발송에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    // 공통
    BAD_REQUEST("잘못된 요청입니다.", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus status;

    ErrorCode(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }

    public String getMessage() { return message; }
    public HttpStatus getStatus() { return status; }
}
