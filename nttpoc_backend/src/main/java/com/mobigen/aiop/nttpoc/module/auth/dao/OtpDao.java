package com.mobigen.aiop.nttpoc.module.auth.dao;

import java.util.Map;

public interface OtpDao {
    void insertOtp(Map<String, Object> param);
    Map<String, Object> selectOtpBySeq(long otpSeq);
    void incrementOtpFailCount(long otpSeq);
    void updateOtpVerified(long otpSeq);
}
