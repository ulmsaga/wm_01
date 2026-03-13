package com.saga.wm.module.auth.dto;

public class LoginResponse {
    private Long userId;
    private String loginId;
    private String userName;
    private String role;

    public LoginResponse(Long userId, String loginId, String userName, String role) {
        this.userId = userId;
        this.loginId = loginId;
        this.userName = userName;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getUserName() {
        return userName;
    }

    public String getRole() {
        return role;
    }
}
