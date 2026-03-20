package com.mobigen.aiop.nttpoc.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorCode")
class ErrorCodeTest {

    @Nested
    @DisplayName("getMessageKey")
    class GetMessageKey {

        @Test
        @DisplayName("UNAUTHORIZED → error.unauthorized 키 반환")
        void unauthorized_messageKey() {
            assertThat(ErrorCode.UNAUTHORIZED.getMessageKey()).isEqualTo("error.unauthorized");
        }

        @Test
        @DisplayName("OTP_MISMATCH → error.otp.mismatch 키 반환")
        void otpMismatch_messageKey() {
            assertThat(ErrorCode.OTP_MISMATCH.getMessageKey()).isEqualTo("error.otp.mismatch");
        }

        @Test
        @DisplayName("모든 ErrorCode는 'error.' 로 시작하는 messageKey를 가짐")
        void allCodes_messageKeyStartsWithError() {
            for (ErrorCode code : ErrorCode.values()) {
                assertThat(code.getMessageKey())
                        .as("ErrorCode." + code.name())
                        .startsWith("error.");
            }
        }
    }

    @Nested
    @DisplayName("getStatus")
    class GetStatus {

        @Test
        @DisplayName("LOGIN_FAILED → 401 UNAUTHORIZED")
        void loginFailed_status401() {
            assertThat(ErrorCode.LOGIN_FAILED.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("BAD_REQUEST → 400 BAD_REQUEST")
        void badRequest_status400() {
            assertThat(ErrorCode.BAD_REQUEST.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("INTERNAL_SERVER_ERROR → 500 INTERNAL_SERVER_ERROR")
        void internalServerError_status500() {
            assertThat(ErrorCode.INTERNAL_SERVER_ERROR.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("ACCESS_DENIED → 403 FORBIDDEN")
        void accessDenied_status403() {
            assertThat(ErrorCode.ACCESS_DENIED.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
