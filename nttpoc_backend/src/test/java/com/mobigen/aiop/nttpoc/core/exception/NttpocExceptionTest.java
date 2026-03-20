package com.mobigen.aiop.nttpoc.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NttpocException")
class NttpocExceptionTest {

    @Nested
    @DisplayName("생성자(ErrorCode)")
    class SingleArgConstructor {

        @Test
        @DisplayName("errorCode 반환")
        void getErrorCode_returnsCode() {
            NttpocException ex = new NttpocException(ErrorCode.LOGIN_FAILED);
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
        }

        @Test
        @DisplayName("args → null")
        void getArgs_null() {
            NttpocException ex = new NttpocException(ErrorCode.LOGIN_FAILED);
            assertThat(ex.getArgs()).isNull();
        }

        @Test
        @DisplayName("message → errorCode.getMessageKey()")
        void message_isMessageKey() {
            NttpocException ex = new NttpocException(ErrorCode.OTP_MISMATCH);
            assertThat(ex.getMessage()).isEqualTo(ErrorCode.OTP_MISMATCH.getMessageKey());
        }
    }

    @Nested
    @DisplayName("생성자(ErrorCode, args...)")
    class VarArgConstructor {

        @Test
        @DisplayName("args 정상 저장")
        void getArgs_returnsArgs() {
            NttpocException ex = new NttpocException(ErrorCode.OTP_MISMATCH, 2);
            assertThat(ex.getArgs()).containsExactly(2);
        }

        @Test
        @DisplayName("errorCode 반환")
        void getErrorCode_returnsCode() {
            NttpocException ex = new NttpocException(ErrorCode.OTP_MISMATCH, 1, "extra");
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OTP_MISMATCH);
        }
    }
}
