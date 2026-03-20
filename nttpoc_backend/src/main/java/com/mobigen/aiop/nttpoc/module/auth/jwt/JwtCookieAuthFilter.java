package com.mobigen.aiop.nttpoc.module.auth.jwt;

import java.io.IOException;
import java.util.List;

import com.mobigen.aiop.nttpoc.core.exception.ErrorCode;
import com.mobigen.aiop.nttpoc.core.handler.SecurityErrorHandler;
import com.mobigen.aiop.nttpoc.module.auth.sse.SseSessionRegistry;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtCookieAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final SecurityErrorHandler securityErrorHandler;
    private final SseSessionRegistry sseSessionRegistry;

    public JwtCookieAuthFilter(JwtProvider jwtProvider,
                               SecurityErrorHandler securityErrorHandler,
                               SseSessionRegistry sseSessionRegistry) {
        this.jwtProvider = jwtProvider;
        this.securityErrorHandler = securityErrorHandler;
        this.sseSessionRegistry = sseSessionRegistry;
    }

    // JWT 없이 접근 가능한 공개 경로만 명시 (me는 제외)
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/rsa-public-key",
            "/api/auth/login",
            "/api/auth/verify-otp",
            "/api/auth/refresh",
            "/api/auth/logout"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PUBLIC_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 이미 Authentication이 있으면 패스 (같은 요청 체인에서 중복 방지)
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = readCookie(request, JwtCookieUtil.ACCESS_COOKIE);
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtProvider.parseAndValidate(token, JwtProvider.TYP_ACCESS);
            String userId = jwtProvider.getSubject(claims);

            // 중복 로그인 kick 이후 발급된 구(舊) AT인지 확인
            // - revokedAt > atIssuedAt 이면 이 세션은 이미 무효화됨 → 401 반환
            // - 쿠키를 삭제하지 않아도 무한루프 방지 가능 (다음 getMe도 동일하게 401)
            // - Caffeine TTL(10분) > AT TTL(5분)이므로 AT 만료 전 항상 차단됨
            java.util.Date issuedAt = claims.getIssuedAt();
            if (issuedAt != null
                    && sseSessionRegistry.isRevokedAfter(userId, issuedAt.toInstant())) {
                securityErrorHandler.writeError(request, response, ErrorCode.TOKEN_INVALID);
                return;
            }

            // 권한은 지금은 심플하게 USER 고정
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
            // SseController 등에서 AT 발급 시각(iat) 조회에 사용
            auth.setDetails(claims);

            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            // 만료된 토큰 — 프론트에서 refresh 시도해야 함
            securityErrorHandler.writeError(request, response, ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException e) {
            // 위변조 / 형식 오류 — 즉시 거부
            securityErrorHandler.writeError(request, response, ErrorCode.TOKEN_INVALID);
        }
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
