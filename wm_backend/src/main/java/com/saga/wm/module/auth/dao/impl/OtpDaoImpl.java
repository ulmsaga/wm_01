package com.saga.wm.module.auth.dao.impl;

import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saga.wm.module.auth.dao.OtpDao;

@Repository
public class OtpDaoImpl implements OtpDao {

    private final SqlSessionTemplate sqlSessionTemplateWmDb1;
    private final String namespace = "com.saga.wm.module.auth";

    @Autowired
    public OtpDaoImpl(SqlSessionTemplate sqlSessionTemplateWmDb1) {
        this.sqlSessionTemplateWmDb1 = sqlSessionTemplateWmDb1;
    }

    @Override
    public void insertOtp(Map<String, Object> param) {
        sqlSessionTemplateWmDb1.insert(namespace + ".insertOtp", param);
    }

    @Override
    public Map<String, Object> selectOtpBySeq(long otpSeq) {
        return sqlSessionTemplateWmDb1.selectOne(namespace + ".selectOtpBySeq", otpSeq);
    }

    @Override
    public void incrementOtpFailCount(long otpSeq) {
        sqlSessionTemplateWmDb1.update(namespace + ".incrementOtpFailCount", otpSeq);
    }

    @Override
    public void updateOtpVerified(long otpSeq) {
        sqlSessionTemplateWmDb1.update(namespace + ".updateOtpVerified", otpSeq);
    }
}
