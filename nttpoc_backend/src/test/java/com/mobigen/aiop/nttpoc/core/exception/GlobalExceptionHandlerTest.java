package com.mobigen.aiop.nttpoc.core.exception;

import com.mobigen.aiop.nttpoc.core.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    @Mock MessageSource messageSource;
    @InjectMocks GlobalExceptionHandler handler;

    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        lenient().when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Nested
    @DisplayName("handleNttpocException")
    class HandleNttpocException {

        @Test
        @DisplayName("NttpocException → ErrorCode HTTP 상태 + fail 응답 반환")
        void wmException_returnsCorrectStatusAndBody() {
            given(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                    .willReturn("로그인에 실패했습니다.");

            NttpocException ex = new NttpocException(ErrorCode.LOGIN_FAILED);
            ResponseEntity<ApiResponse<Void>> response = handler.handleNttpocException(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isFalse();
            assertThat(response.getBody().code()).isEqualTo("LOGIN_FAILED");
            assertThat(response.getBody().message()).isEqualTo("로그인에 실패했습니다.");
        }

        @Test
        @DisplayName("OTP_MISMATCH + args → MessageSource에 args 전달")
        void wmExceptionWithArgs_passesArgsToMessageSource() {
            given(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                    .willReturn("OTP 오류. 남은 횟수: 2");

            NttpocException ex = new NttpocException(ErrorCode.OTP_MISMATCH, 2);
            ResponseEntity<ApiResponse<Void>> response = handler.handleNttpocException(ex, request);

            assertThat(response.getBody().code()).isEqualTo("OTP_MISMATCH");
            assertThat(response.getBody().message()).isEqualTo("OTP 오류. 남은 횟수: 2");
        }

        @Test
        @DisplayName("FORBIDDEN(ACCESS_DENIED) → 403 상태")
        void accessDenied_returns403() {
            given(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                    .willReturn("접근 거부됨");

            NttpocException ex = new NttpocException(ErrorCode.ACCESS_DENIED);
            ResponseEntity<ApiResponse<Void>> response = handler.handleNttpocException(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("handleException")
    class HandleException {

        @Test
        @DisplayName("예상치 못한 예외 → 500 + INTERNAL_SERVER_ERROR 응답")
        void unexpectedException_returns500() {
            given(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                    .willReturn("서버 오류가 발생했습니다.");

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleException(new RuntimeException("unexpected"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
        }
    }

    @Nested
    @DisplayName("SSE 예외 처리")
    class SseExceptions {

        @Test
        @DisplayName("AsyncRequestTimeoutException → void 반환 (예외 없음)")
        void asyncTimeout_noException() {
            handler.handleAsyncTimeout(new AsyncRequestTimeoutException());
            // 반환값 없음, 예외만 없으면 통과
        }

        @Test
        @DisplayName("AsyncRequestNotUsableException → void 반환 (예외 없음)")
        void asyncNotUsable_noException() {
            handler.handleAsyncNotUsable(new AsyncRequestNotUsableException("broken pipe", new Exception()));
        }
    }
}
