package com.saga.wm.module.auth.dto;

public class LoginRequest {
    private String loginId;
    private String password;

    public String getLoginId() {
        return loginId;
    }
    public String getPassword() {
        return password;
    }
    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }
    public void setPassword(String password) {
        this.password = password;
    }
}
