package com.saga.wm.module.auth.dao;

import java.util.Map;

public interface RefreshTokenDao {
    int insert(Map<String, Object> p);
    Map<String, Object> selectByJtiHash(Map<String, Object> p);
    int revokeByJtiHash(Map<String, Object> p);
    int rotate(Map<String, Object> p);
    int revokeAllByUserId(Map<String, Object> p);
    int deleteExpired(Map<String, Object> p);
}