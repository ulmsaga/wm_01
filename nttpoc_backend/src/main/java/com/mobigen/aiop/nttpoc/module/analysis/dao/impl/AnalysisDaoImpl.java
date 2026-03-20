package com.mobigen.aiop.nttpoc.module.analysis.dao.impl;

import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.mobigen.aiop.nttpoc.module.analysis.dao.AnalysisDao;

@Repository
public class AnalysisDaoImpl implements AnalysisDao {

    private final SqlSessionTemplate sqlSessionTemplateNttpocDb1;
    private final String namespace = "com.mobigen.aiop.nttpoc.module.analysis";

    @Autowired
    public AnalysisDaoImpl(SqlSessionTemplate sqlSessionTemplateNttpocDb1) {
        this.sqlSessionTemplateNttpocDb1 = sqlSessionTemplateNttpocDb1;
    }

    @Override
    public String getCurrTime(Map<String, Object> param) {
        // throw new UnsupportedOperationException("Not supported yet.");
        return sqlSessionTemplateNttpocDb1.selectOne(namespace + ".selectNow", param);
    }

}
