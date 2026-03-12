package com.saga.wm.module.auth.dao.impl;

import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saga.wm.module.auth.dao.RefreshTokenDao;

@Repository
public class RefreshTokenDaoImpl implements RefreshTokenDao {

    private final SqlSessionTemplate sqlSessionTemplateWmDb1;
    private final String namespace = "com.saga.wm.module.auth";

    @Autowired
    public RefreshTokenDaoImpl(SqlSessionTemplate sqlSessionTemplateWmDb1) {
        this.sqlSessionTemplateWmDb1 = sqlSessionTemplateWmDb1;
    }

    @Override
    public int insert(Map<String, Object> p) {
        return sqlSessionTemplateWmDb1.insert(namespace + ".insertRefreshToken", p);
    }

    @Override
    public Map<String, Object> selectByJtiHash(Map<String, Object> p) {
        return sqlSessionTemplateWmDb1.selectOne(namespace + ".selectRefreshTokenByJtiHash", p);
    }

    @Override
    public int revokeByJtiHash(Map<String, Object> p) {
        return sqlSessionTemplateWmDb1.update(namespace + ".revokeRefreshTokenByJtiHash", p);
    }

    @Override
    public int rotate(Map<String, Object> p) {
        return sqlSessionTemplateWmDb1.update(namespace + ".rotateRefreshToken", p);
    }

    @Override
    public int revokeAllByUserId(Map<String, Object> p) {
        return sqlSessionTemplateWmDb1.update(namespace + ".revokeAllRefreshTokensByUserId", p);
    }

    @Override
    public int deleteExpired(Map<String, Object> p) {
        return sqlSessionTemplateWmDb1.delete(namespace + ".deleteExpiredRefreshTokens", p);
    }
}