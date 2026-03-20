package com.mobigen.aiop.nttpoc.core.util;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * 일회용 RSA 키쌍 생성/관리.
 * - 공개키: 클라이언트로 전달
 * - 개인키: Caffeine 메모리 캐시 (TTL 5분, 사용 후 즉시 삭제)
 */
@Component
public class RsaKeyUtil {

    private static final String RSA_ALGORITHM = "RSA";
    private static final int KEY_SIZE = 2048;
    private static final OAEPParameterSpec OAEP_PARAMS = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private final Cache<String, PrivateKey> privateKeyCache;

    public RsaKeyUtil() {
        this.privateKeyCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();
    }

    public record RsaPublicKeyInfo(String keyId, String publicKeyBase64) {}

    /** RSA 2048 키쌍 생성 → 공개키 반환, 개인키 캐시 저장 */
    public RsaPublicKeyInfo generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            generator.initialize(KEY_SIZE);
            KeyPair keyPair = generator.generateKeyPair();

            String keyId = UUID.randomUUID().toString();
            privateKeyCache.put(keyId, keyPair.getPrivate());

            String publicKeyBase64 = Base64.getEncoder().encodeToString(
                    keyPair.getPublic().getEncoded());

            return new RsaPublicKeyInfo(keyId, publicKeyBase64);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA key generation failed", e);
        }
    }

    /** 일회용 복호화: keyId로 개인키 조회 → 복호화 → 즉시 삭제 */
    public String decrypt(String keyId, String encryptedBase64) {
        PrivateKey privateKey = privateKeyCache.getIfPresent(keyId);
        if (privateKey == null) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 키입니다.");
        }

        // 일회용: 사용 후 즉시 삭제
        privateKeyCache.invalidate(keyId);

        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_PARAMS);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedBase64));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException e) {
            throw new IllegalArgumentException("복호화에 실패했습니다.", e);
        }
    }
}
