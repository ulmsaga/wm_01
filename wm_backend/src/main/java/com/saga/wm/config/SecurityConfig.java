package com.saga.wm.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.saga.wm.module.auth.jwt.JwtCookieAuthFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtCookieAuthFilter jwtCookieAuthFilter;

    public SecurityConfig(JwtCookieAuthFilter jwtCookieAuthFilter) {
        this.jwtCookieAuthFilter = jwtCookieAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CORS (쿠키 기반이면 필수)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // MVP 단계에서는 csrf disable로 시작(운영 전 재검토)
            .csrf(csrf -> csrf.disable())

            // 세션 미사용
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 401/403 응답 통일
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"message\":\"UNAUTHORIZED\"}");
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"message\":\"FORBIDDEN\"}");
                })
            )

            // 권한 규칙
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/error", "/health").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtCookieAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // BasicAuth 끄기 (지금은 필요 없음)
            .httpBasic(basic -> basic.disable());

        // JWT 쿠키 인증 필터 등록
        // http.addFilterBefore(jwtCookieAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}