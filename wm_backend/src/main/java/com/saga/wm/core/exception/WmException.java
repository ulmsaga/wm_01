package com.saga.wm.core.exception;

public class WmException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] args;

    public WmException(ErrorCode errorCode) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
        this.args = null;
    }

    /** MessageSource 메시지 인수가 필요할 때 (예: OTP 남은 시도 횟수) */
    public WmException(ErrorCode errorCode, Object... args) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
        this.args = args;
    }

    public ErrorCode getErrorCode() { return errorCode; }

    /** MessageSource.getMessage() 호출 시 전달할 인수 배열 */
    public Object[] getArgs() { return args; }
}
