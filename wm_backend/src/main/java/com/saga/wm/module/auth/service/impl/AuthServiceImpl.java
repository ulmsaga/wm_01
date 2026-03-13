package com.saga.wm.module.auth.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saga.wm.module.auth.dao.AuthDao;
import com.saga.wm.module.auth.service.AuthService;

@Service
public class AuthServiceImpl implements AuthService {

    @SuppressWarnings("unused")
    private final AuthDao authDao;

    @Autowired
    public AuthServiceImpl(AuthDao authDao) {
        this.authDao = authDao;
    }
    
}
