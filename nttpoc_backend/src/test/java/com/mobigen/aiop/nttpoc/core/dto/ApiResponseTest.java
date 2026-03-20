package com.mobigen.aiop.nttpoc.core.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse")
class ApiResponseTest {

    @Nested
    @DisplayName("ok()")
    class Ok {

        @Test
        @DisplayName("success=true, data 포함, code/message null")
        void ok_successTrue_dataSet() {
            ApiResponse<String> res = ApiResponse.ok("hello");
            assertThat(res.success()).isTrue();
            assertThat(res.data()).isEqualTo("hello");
            assertThat(res.code()).isNull();
            assertThat(res.message()).isNull();
        }

        @Test
        @DisplayName("data=null 허용")
        void ok_nullData() {
            ApiResponse<Void> res = ApiResponse.ok(null);
            assertThat(res.success()).isTrue();
            assertThat(res.data()).isNull();
        }
    }

    @Nested
    @DisplayName("fail()")
    class Fail {

        @Test
        @DisplayName("success=false, code/message 포함, data null")
        void fail_successFalse_codeAndMessageSet() {
            ApiResponse<Void> res = ApiResponse.fail("TOKEN_EXPIRED", "만료된 토큰입니다.");
            assertThat(res.success()).isFalse();
            assertThat(res.code()).isEqualTo("TOKEN_EXPIRED");
            assertThat(res.message()).isEqualTo("만료된 토큰입니다.");
            assertThat(res.data()).isNull();
        }
    }
}
