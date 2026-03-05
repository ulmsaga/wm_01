package com.saga.wm.module.auth.jwt;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 쿠키의 access_token을 읽어 인증을 세팅하는 필터.
 * - access 토큰만 검증 (refresh는 /api/auth/refresh에서 처리)
 * - 유효하면 SecurityContext에 Authentication을 넣어준다.
 */
@Component
public class JwtCookieAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    public JwtCookieAuthFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // CORS preflight는 필터 스킵
        if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;

        // 인증 자체는 permitAll이지만, 굳이 필터에서 토큰 검증할 필요 없어서 스킵(선택)
        if (path.startsWith("/api/auth/")) return true;

        // 그 외에는 필터 적용
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 이미 인증이 세팅되어 있으면 패스
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String accessToken = readCookie(request, JwtCookieUtil.COOKIE_ACCESS);

        // 토큰이 없거나 유효하지 않으면 그냥 다음으로(= Security에서 401 처리)
        if (accessToken == null || !jwtProvider.isValidAccessToken(accessToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        long kakaoUserId = jwtProvider.getUserId(accessToken);

        // 지금은 ROLE_USER 고정 (나중에 allowlist role 넣을 수도)
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

        var authentication =
                new UsernamePasswordAuthenticationToken(kakaoUserId, null, authorities);

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}