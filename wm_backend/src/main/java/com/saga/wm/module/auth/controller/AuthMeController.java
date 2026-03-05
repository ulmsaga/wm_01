package com.saga.wm.module.auth.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthMeController {

    public AuthMeController() {
    }
    
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        Map<String, Object> ret = Map.of(
            "authenticated", authentication != null && authentication.isAuthenticated(),
            "principal", authentication != null ? authentication.getPrincipal() : null
        );
        return ResponseEntity.ok(ret);
    }
}
