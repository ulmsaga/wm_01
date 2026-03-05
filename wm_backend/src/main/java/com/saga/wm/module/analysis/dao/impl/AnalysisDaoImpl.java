package com.saga.wm.module.analysis.dao.impl;

import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saga.wm.module.analysis.dao.AnalysisDao;

@Repository
public class AnalysisDaoImpl implements AnalysisDao {

    private final SqlSessionTemplate sqlSessionTemplateWmDb1;
    private final String namespace = "com.saga.wm.module.analysis";

    @Autowired
    public AnalysisDaoImpl(SqlSessionTemplate sqlSessionTemplateWmDb1) {
        this.sqlSessionTemplateWmDb1 = sqlSessionTemplateWmDb1;
    }

    @Override
    public String getCurrTime(Map<String, Object> param) {
        // throw new UnsupportedOperationException("Not supported yet.");
        return sqlSessionTemplateWmDb1.selectOne(namespace + ".selectNow", param);
    }
        
}
