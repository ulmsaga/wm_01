package com.saga.wm.module.auth.dto;

public class LoginRequest {
    private String keyId;
    private String encryptedCredentials;
    /** 중복 로그인 확인 팝업에서 사용자가 동의한 경우 true. 기본값 false. */
    private boolean forceLogin = false;

    public String getKeyId() { return keyId; }
    public String getEncryptedCredentials() { return encryptedCredentials; }
    public boolean isForceLogin() { return forceLogin; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public void setEncryptedCredentials(String encryptedCredentials) { this.encryptedCredentials = encryptedCredentials; }
    public void setForceLogin(boolean forceLogin) { this.forceLogin = forceLogin; }
}
