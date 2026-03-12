package com.saga.wm.module.analysis.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saga.wm.module.analysis.service.AnalysisService;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    @Autowired
    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/getCurrTime")
    public ResponseEntity<String> getCurrTime(@RequestParam Map<String, Object> param) {
        String ret = analysisService.getCurrTime(param);
        return ResponseEntity.ok(ret);
    }
}
