package com.saga.wm.module.auth.dto;

public class MeResponse {
    private boolean authenticated;
    private Long userId;
    private String loginId;
    private String userName;
    private String role;

    public MeResponse(boolean authenticated, Long userId, String loginId, String userName, String role) {
        this.authenticated = authenticated;
        this.userId = userId;
        this.loginId = loginId;
        this.userName = userName;
        this.role = role;
    }

    public boolean isAuthenticated() {
        return authenticated;
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
