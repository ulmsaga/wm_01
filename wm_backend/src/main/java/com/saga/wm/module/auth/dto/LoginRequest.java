package com.saga.wm.module.auth.dto;

public class LoginRequest {
    private String keyId;
    private String encryptedCredentials;

    public String getKeyId() { return keyId; }
    public String getEncryptedCredentials() { return encryptedCredentials; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public void setEncryptedCredentials(String encryptedCredentials) { this.encryptedCredentials = encryptedCredentials; }
}
