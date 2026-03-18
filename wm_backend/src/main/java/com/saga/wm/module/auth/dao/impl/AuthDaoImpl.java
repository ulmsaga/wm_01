package com.saga.wm.module.auth.dao.impl;

import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saga.wm.module.auth.dao.AuthDao;

@Repository
public class AuthDaoImpl implements AuthDao {

    private final SqlSessionTemplate sqlSessionTemplateWmDb1;
    private final String namespace = "com.saga.wm.module.auth";

    @Autowired
    public AuthDaoImpl(SqlSessionTemplate sqlSessionTemplateWmDb1) {
        this.sqlSessionTemplateWmDb1 = sqlSessionTemplateWmDb1;
    }

    @Override
    public Map<String, Object> selectUserForLogin(Map<String, Object> param) {
        return sqlSessionTemplateWmDb1.selectOne(namespace + ".selectUserForLogin", param);
    }

    @Override
    public void updateLoginSuccess(Map<String, Object> param) {
        sqlSessionTemplateWmDb1.update(namespace + ".updateLoginSuccess", param);
    }

    @Override
    public void updateLoginFailure(Map<String, Object> param) {
        sqlSessionTemplateWmDb1.update(namespace + ".updateLoginFailure", param);
    }

    @Override
    public void updateUserLastLoginAt(Map<String, Object> param) {
        sqlSessionTemplateWmDb1.update(namespace + ".updateUserLastLoginAt", param);
    }

    @Override
    public Map<String, Object> selectUserInfoByUserSeq(long userSeq) {
        return sqlSessionTemplateWmDb1.selectOne(namespace + ".selectUserInfoByUserSeq", userSeq);
    }

    @Override
    public String selectAllowDuplicateLogin(long userSeq) {
        return sqlSessionTemplateWmDb1.selectOne(namespace + ".selectAllowDuplicateLogin", userSeq);
    }
}
