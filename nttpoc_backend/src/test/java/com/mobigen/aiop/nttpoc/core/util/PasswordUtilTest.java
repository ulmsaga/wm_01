package com.mobigen.aiop.nttpoc.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordUtil")
class PasswordUtilTest {

    @Mock PasswordEncoder encoder;
    @InjectMocks PasswordUtil passwordUtil;

    @Test
    @DisplayName("encode → PasswordEncoder.encode 위임")
    void encode_delegatesToEncoder() {
        given(encoder.encode("raw")).willReturn("hashed");

        assertThat(passwordUtil.encode("raw")).isEqualTo("hashed");
    }

    @Test
    @DisplayName("matches → PasswordEncoder.matches 위임")
    void matches_delegatesToEncoder() {
        given(encoder.matches("raw", "hashed")).willReturn(true);

        assertThat(passwordUtil.matches("raw", "hashed")).isTrue();
    }
}
