package com.saga.wm.module.auth.dao;

import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

public interface AuthDao {
    Map<String, Object> login(String loginId, String password, HttpServletResponse response);
}
