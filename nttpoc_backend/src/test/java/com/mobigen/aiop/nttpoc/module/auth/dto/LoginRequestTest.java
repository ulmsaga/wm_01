package com.mobigen.aiop.nttpoc.module.auth.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoginRequest")
class LoginRequestTest {

    @Test
    @DisplayName("기본값: forceLogin=false, 나머지 null")
    void defaultValues() {
        LoginRequest req = new LoginRequest();

        assertThat(req.getKeyId()).isNull();
        assertThat(req.getEncryptedCredentials()).isNull();
        assertThat(req.isForceLogin()).isFalse();
    }

    @Test
    @DisplayName("setter/getter 정상 동작")
    void setterAndGetter() {
        LoginRequest req = new LoginRequest();
        req.setKeyId("key-1");
        req.setEncryptedCredentials("enc-data");
        req.setForceLogin(true);

        assertThat(req.getKeyId()).isEqualTo("key-1");
        assertThat(req.getEncryptedCredentials()).isEqualTo("enc-data");
        assertThat(req.isForceLogin()).isTrue();
    }
}
