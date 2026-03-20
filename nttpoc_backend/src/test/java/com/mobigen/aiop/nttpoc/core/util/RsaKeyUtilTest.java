package com.mobigen.aiop.nttpoc.core.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RsaKeyUtil")
class RsaKeyUtilTest {

    private RsaKeyUtil rsaKeyUtil;

    @BeforeEach
    void setUp() {
        rsaKeyUtil = new RsaKeyUtil();
    }

    @Nested
    @DisplayName("generateKeyPair")
    class GenerateKeyPair {

        @Test
        @DisplayName("keyId non-blank, publicKeyBase64 non-blank 반환")
        void generateKeyPair_returnsNonBlankValues() {
            RsaKeyUtil.RsaPublicKeyInfo info = rsaKeyUtil.generateKeyPair();
            assertThat(info.keyId()).isNotBlank();
            assertThat(info.publicKeyBase64()).isNotBlank();
        }

        @Test
        @DisplayName("호출마다 다른 keyId 반환 (고유성)")
        void generateKeyPair_uniqueKeyIds() {
            RsaKeyUtil.RsaPublicKeyInfo a = rsaKeyUtil.generateKeyPair();
            RsaKeyUtil.RsaPublicKeyInfo b = rsaKeyUtil.generateKeyPair();
            assertThat(a.keyId()).isNotEqualTo(b.keyId());
        }

        @Test
        @DisplayName("publicKeyBase64 → 유효한 RSA 공개키 디코딩 가능")
        void generateKeyPair_validPublicKey() throws Exception {
            RsaKeyUtil.RsaPublicKeyInfo info = rsaKeyUtil.generateKeyPair();
            byte[] keyBytes = Base64.getDecoder().decode(info.publicKeyBase64());
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
            assertThat(publicKey).isNotNull();
        }
    }

    @Nested
    @DisplayName("decrypt")
    class Decrypt {

        @Test
        @DisplayName("generateKeyPair 후 암호화한 데이터 → 정상 복호화")
        void decrypt_success() throws Exception {
            RsaKeyUtil.RsaPublicKeyInfo info = rsaKeyUtil.generateKeyPair();
            String plain = "{\"loginId\":\"user\",\"password\":\"pass\"}";
            String encrypted = encrypt(info.publicKeyBase64(), plain);

            String result = rsaKeyUtil.decrypt(info.keyId(), encrypted);
            assertThat(result).isEqualTo(plain);
        }

        @Test
        @DisplayName("복호화 후 동일 keyId 재사용 → IllegalArgumentException (일회용)")
        void decrypt_oneTimeUse() throws Exception {
            RsaKeyUtil.RsaPublicKeyInfo info = rsaKeyUtil.generateKeyPair();
            String encrypted = encrypt(info.publicKeyBase64(), "data");
            rsaKeyUtil.decrypt(info.keyId(), encrypted); // 첫 번째 사용

            assertThatThrownBy(() -> rsaKeyUtil.decrypt(info.keyId(), encrypted))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("존재하지 않는 keyId → IllegalArgumentException")
        void decrypt_unknownKeyId() {
            assertThatThrownBy(() -> rsaKeyUtil.decrypt("unknown-key-id", "dummy"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("잘못된 암호문 → IllegalArgumentException")
        void decrypt_invalidCiphertext() throws Exception {
            RsaKeyUtil.RsaPublicKeyInfo info = rsaKeyUtil.generateKeyPair();
            assertThatThrownBy(() -> rsaKeyUtil.decrypt(info.keyId(), "not-valid-base64!!!"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // 테스트용 RSA-OAEP 암호화 헬퍼
    private static String encrypt(String publicKeyBase64, String plainText) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(keyBytes));

        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams);

        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }
}
