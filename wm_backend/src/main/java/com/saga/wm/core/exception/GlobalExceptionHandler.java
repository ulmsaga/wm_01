package com.saga.wm.core.exception;

import com.saga.wm.core.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    @Autowired
    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** 비즈니스 예외 — 의도된 에러 (로그인 실패, OTP 오류 등) */
    @ExceptionHandler(WmException.class)
    public ResponseEntity<ApiResponse<Void>> handleWmException(
            WmException e, HttpServletRequest request) {
        String message = messageSource.getMessage(
                e.getErrorCode().getMessageKey(), e.getArgs(), LocaleContextHolder.getLocale());
        log.warn("[WmException] uri={} code={} message={}",
                request.getRequestURI(), e.getErrorCode().name(), message);
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ApiResponse.fail(e.getErrorCode().name(), message));
    }

    /**
     * SSE 장기 연결 타임아웃 (주로 서버 종료 시).
     * GlobalExceptionHandler가 ApiResponse를 text/event-stream으로 쓰려다 실패하는 것을 차단.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException e) {
        log.debug("[SSE 타임아웃] AsyncRequestTimeoutException - 서버 종료 또는 연결 타임아웃");
    }

    /**
     * SSE Broken pipe — 클라이언트가 이미 연결을 끊은 후 쓰기 시도 시 발생.
     * heartbeat/send 중 발생하며 정상 클라이언트 disconnect이므로 debug 레벨로만 기록.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncNotUsable(AsyncRequestNotUsableException e) {
        log.debug("[SSE Broken pipe] AsyncRequestNotUsableException - 클라이언트 연결 종료");
    }

    /** 그 외 모든 예외 — 예상치 못한 서버 오류 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception e, HttpServletRequest request) {
        log.error("[UnhandledException] uri={}", request.getRequestURI(), e);
        ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;
        String message = messageSource.getMessage(
                code.getMessageKey(), null, LocaleContextHolder.getLocale());
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.fail(code.name(), message));
    }
}
