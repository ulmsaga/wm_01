package com.mobigen.aiop.nttpoc.module.auth.dao.impl;

import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.mobigen.aiop.nttpoc.module.auth.dao.OtpDao;

@Repository
public class OtpDaoImpl implements OtpDao {

    private final SqlSessionTemplate sqlSessionTemplateNttpocDb1;
    private final String namespace = "com.mobigen.aiop.nttpoc.module.auth";

    @Autowired
    public OtpDaoImpl(SqlSessionTemplate sqlSessionTemplateNttpocDb1) {
        this.sqlSessionTemplateNttpocDb1 = sqlSessionTemplateNttpocDb1;
    }

    @Override
    public void insertOtp(Map<String, Object> param) {
        sqlSessionTemplateNttpocDb1.insert(namespace + ".insertOtp", param);
    }

    @Override
    public Map<String, Object> selectOtpBySeq(long otpSeq) {
        return sqlSessionTemplateNttpocDb1.selectOne(namespace + ".selectOtpBySeq", otpSeq);
    }

    @Override
    public void incrementOtpFailCount(long otpSeq) {
        sqlSessionTemplateNttpocDb1.update(namespace + ".incrementOtpFailCount", otpSeq);
    }

    @Override
    public void updateOtpVerified(long otpSeq) {
        sqlSessionTemplateNttpocDb1.update(namespace + ".updateOtpVerified", otpSeq);
    }
}
