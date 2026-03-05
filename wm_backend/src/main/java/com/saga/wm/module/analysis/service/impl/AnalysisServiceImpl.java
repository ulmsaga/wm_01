package com.saga.wm.module.analysis.service.impl;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saga.wm.module.analysis.dao.AnalysisDao;
import com.saga.wm.module.analysis.service.AnalysisService;

@Service
public class AnalysisServiceImpl implements  AnalysisService {

    private final AnalysisDao analysisDao;

    @Autowired
    public AnalysisServiceImpl(AnalysisDao analysisDao) {
        this.analysisDao = analysisDao;
    }

    @Override
    public String getCurrTime(Map<String, Object> param) {
        return analysisDao.getCurrTime(param);
    }
    
}
