package com.saga.wm.module.auth.dao;

import java.util.Map;

public interface RefreshTokenDao {
    int insert(Map<String, Object> p);
    Map<String, Object> selectByJtiHash(Map<String, Object> p);
    int revokeByJtiHash(Map<String, Object> p);
    int rotate(Map<String, Object> p);
    int revokeAllByUserSeq(Map<String, Object> p);
    int deleteExpired(Map<String, Object> p);
    int countActiveSessions(Map<String, Object> p);

    /** 서버 재기동 시 전체 활성 세션 일괄 무효화 */
    int revokeAllActiveSessions();
}