package com.saga.wm.module.auth.dao;

import java.util.Map;

public interface AuthDao {
    Map<String, Object> selectUserForLogin(Map<String, Object> param);
    void updateLoginSuccess(Map<String, Object> param);
    void updateLoginFailure(Map<String, Object> param);
    void updateUserLastLoginAt(Map<String, Object> param);
    Map<String, Object> selectUserInfoByUserSeq(long userSeq);
}
