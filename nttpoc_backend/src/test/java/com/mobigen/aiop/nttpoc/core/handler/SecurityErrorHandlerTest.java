package com.mobigen.aiop.nttpoc.core.handler;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import com.mobigen.aiop.nttpoc.core.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityErrorHandler")
class SecurityErrorHandlerTest {

    @Mock ObjectMapper objectMapper;
    @Mock MessageSource messageSource;
    @InjectMocks SecurityErrorHandler handler;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws IOException {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseBody = new StringWriter();
        lenient().when(request.getRequestURI()).thenReturn("/api/test");
        lenient().when(request.getLocale()).thenReturn(Locale.KOREAN);
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("에러 메시지");
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"success\":false}");
    }

    @Nested
    @DisplayName("commence (401)")
    class Commence {

        @Test
        @DisplayName("미인증 접근 → 401 상태 설정 + JSON 응답 출력")
        void commence_sets401AndWritesJson() throws IOException {
            AuthenticationException ex = mock(AuthenticationException.class);
            given(ex.getMessage()).willReturn("unauthorized");

            handler.commence(request, response, ex);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(response).setContentType("application/json;charset=UTF-8");
        }
    }

    @Nested
    @DisplayName("handle (403)")
    class Handle {

        @Test
        @DisplayName("권한 부족 접근 → 403 상태 설정 + JSON 응답 출력")
        void handle_sets403AndWritesJson() throws IOException {
            AccessDeniedException ex = mock(AccessDeniedException.class);
            given(ex.getMessage()).willReturn("access denied");

            handler.handle(request, response, ex);

            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            verify(response).setContentType("application/json;charset=UTF-8");
        }
    }

    @Nested
    @DisplayName("writeError")
    class WriteError {

        @Test
        @DisplayName("TOKEN_EXPIRED → 401 상태 + JSON 출력")
        void writeError_tokenExpired() throws IOException {
            handler.writeError(request, response, ErrorCode.TOKEN_EXPIRED);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(response).setContentType("application/json;charset=UTF-8");
        }

        @Test
        @DisplayName("ACCESS_DENIED → 403 상태")
        void writeError_accessDenied() throws IOException {
            handler.writeError(request, response, ErrorCode.ACCESS_DENIED);

            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
