package com.saga.wm.core.exception;

public class WmException extends RuntimeException {

    private final ErrorCode errorCode;

    public WmException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 에러 코드의 기본 메시지 대신 커스텀 메시지가 필요할 때 (예: OTP 남은 횟수 포함) */
    public WmException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
