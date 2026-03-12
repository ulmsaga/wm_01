package com.saga.wm.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.saga.wm.module.auth.jwt.JwtCookieUtil;
import com.saga.wm.module.auth.jwt.JwtProvider;

@Configuration
public class JwtConfig {

    @Bean
    public JwtProvider jwtProvider(
            @Value("${app.jwt.issuer:wm}") String issuer,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.accessMinutes:30}") int accessMinutes,
            @Value("${app.jwt.refreshDays:30}") int refreshDays) {
        return new JwtProvider(issuer, secret, accessMinutes, refreshDays);
    }

    @Bean
    public JwtCookieUtil jwtCookieUtil(
            // 로컬이면 false, 운영(https)이면 true로 바꾸기
            @Value("${app.jwt.cookieSecure:false}") boolean cookieSecure,
            @Value("${app.jwt.sameSite:Lax}") String sameSite) {
        return new JwtCookieUtil(cookieSecure, sameSite);
    }

    // 필요하면 TTL도 Bean으로 제공
    @Bean(name = "accessTtl")
    public Duration accessTtl(@Value("${app.jwt.accessMinutes:30}") int minutes) {
        return Duration.ofMinutes(minutes);
    }

    @Bean(name = "refreshTtl")
    public Duration refreshTtl(@Value("${app.jwt.refreshDays:30}") int days) {
        return Duration.ofDays(days);
    }
}