package com.mobigen.aiop.nttpoc.module.auth.dao.impl;

import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.mobigen.aiop.nttpoc.module.auth.dao.RefreshTokenDao;

@Repository
public class RefreshTokenDaoImpl implements RefreshTokenDao {

    private final SqlSessionTemplate sqlSessionTemplateNttpocDb1;
    private final String namespace = "com.mobigen.aiop.nttpoc.module.auth";

    @Autowired
    public RefreshTokenDaoImpl(SqlSessionTemplate sqlSessionTemplateNttpocDb1) {
        this.sqlSessionTemplateNttpocDb1 = sqlSessionTemplateNttpocDb1;
    }

    @Override
    public int insert(Map<String, Object> p) {
        return sqlSessionTemplateNttpocDb1.insert(namespace + ".insertRefreshToken", p);
    }

    @Override
    public Map<String, Object> selectByJtiHash(Map<String, Object> p) {
        return sqlSessionTemplateNttpocDb1.selectOne(namespace + ".selectRefreshTokenByJtiHash", p);
    }

    @Override
    public int revokeByJtiHash(Map<String, Object> p) {
        return sqlSessionTemplateNttpocDb1.update(namespace + ".revokeRefreshTokenByJtiHash", p);
    }

    @Override
    public int rotate(Map<String, Object> p) {
        return sqlSessionTemplateNttpocDb1.update(namespace + ".rotateRefreshToken", p);
    }

    @Override
    public int revokeAllByUserSeq(Map<String, Object> p) {
        return sqlSessionTemplateNttpocDb1.update(namespace + ".revokeAllRefreshTokensByUserSeq", p);
    }

    @Override
    public int deleteExpired(Map<String, Object> p) {
        return sqlSessionTemplateNttpocDb1.delete(namespace + ".deleteExpiredRefreshTokens", p);
    }

    @Override
    public int countActiveSessions(Map<String, Object> p) {
        return sqlSessionTemplateNttpocDb1.selectOne(namespace + ".countActiveSessions", p);
    }

    @Override
    public int revokeAllActiveSessions() {
        return sqlSessionTemplateNttpocDb1.update(namespace + ".revokeAllActiveSessions");
    }
}
