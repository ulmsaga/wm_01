package com.mobigen.aiop.nttpoc.module.auth.dao.impl;

import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.mobigen.aiop.nttpoc.module.auth.dao.AuthDao;

@Repository
public class AuthDaoImpl implements AuthDao {

    private final SqlSessionTemplate sqlSessionTemplateNttpocDb1;
    private final String namespace = "com.mobigen.aiop.nttpoc.module.auth";

    @Autowired
    public AuthDaoImpl(SqlSessionTemplate sqlSessionTemplateNttpocDb1) {
        this.sqlSessionTemplateNttpocDb1 = sqlSessionTemplateNttpocDb1;
    }

    @Override
    public Map<String, Object> selectUserForLogin(Map<String, Object> param) {
        return sqlSessionTemplateNttpocDb1.selectOne(namespace + ".selectUserForLogin", param);
    }

    @Override
    public void updateLoginSuccess(Map<String, Object> param) {
        sqlSessionTemplateNttpocDb1.update(namespace + ".updateLoginSuccess", param);
    }

    @Override
    public void updateLoginFailure(Map<String, Object> param) {
        sqlSessionTemplateNttpocDb1.update(namespace + ".updateLoginFailure", param);
    }

    @Override
    public void updateUserLastLoginAt(Map<String, Object> param) {
        sqlSessionTemplateNttpocDb1.update(namespace + ".updateUserLastLoginAt", param);
    }

    @Override
    public Map<String, Object> selectUserInfoByUserSeq(long userSeq) {
        return sqlSessionTemplateNttpocDb1.selectOne(namespace + ".selectUserInfoByUserSeq", userSeq);
    }

    @Override
    public String selectAllowDuplicateLogin(long userSeq) {
        return sqlSessionTemplateNttpocDb1.selectOne(namespace + ".selectAllowDuplicateLogin", userSeq);
    }
}
