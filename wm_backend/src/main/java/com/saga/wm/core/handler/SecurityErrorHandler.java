package com.saga.wm.core.handler;

import com.saga.wm.core.dto.ApiResponse;
import com.saga.wm.core.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Spring Security 필터 레벨 에러 처리 (GlobalExceptionHandler 도달 불가 영역)
 *
 * - AuthenticationEntryPoint : 미인증 요청이 보호된 리소스 접근 시 (401)
 * - AccessDeniedHandler      : 인증은 됐지만 권한 없음 (403)
 * - writeError()             : JwtCookieAuthFilter에서도 동일 포맷으로 응답
 *
 * ※ Security 필터는 DispatcherServlet 이전에 실행되므로 LocaleContextHolder 미사용.
 *   request.getLocale() (= Accept-Language 헤더 기반) 로 locale 결정.
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityErrorHandler.class);

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public SecurityErrorHandler(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
    }

    /** 401 - 인증 없이 보호 리소스 접근 */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("[Security 401] uri={} reason={}", request.getRequestURI(), authException.getMessage());
        writeError(request, response, ErrorCode.UNAUTHORIZED);
    }

    /** 403 - 권한 부족 */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("[Security 403] uri={} reason={}", request.getRequestURI(), accessDeniedException.getMessage());
        writeError(request, response, ErrorCode.ACCESS_DENIED);
    }

    /** JWT 필터에서도 사용하는 공통 에러 응답 writer */
    public void writeError(HttpServletRequest request, HttpServletResponse response,
                           ErrorCode errorCode) throws IOException {
        String message = messageSource.getMessage(
                errorCode.getMessageKey(), null, request.getLocale());
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> body = ApiResponse.fail(errorCode.name(), message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
