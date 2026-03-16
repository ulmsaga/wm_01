package com.saga.wm.module.auth.dao.impl;

import java.util.Map;

import org.springframework.stereotype.Repository;

import com.saga.wm.module.auth.dao.AuthDao;

import jakarta.servlet.http.HttpServletResponse;

@Repository
public class AuthDaoImpl implements AuthDao {

    public AuthDaoImpl() {
    }

    @Override
    public Map<String, Object> login(String loginId, String password, HttpServletResponse response) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    
    
}
