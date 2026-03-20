package com.mobigen.aiop.nttpoc.module.auth.jwt;

import com.mobigen.aiop.nttpoc.core.exception.ErrorCode;
import com.mobigen.aiop.nttpoc.core.handler.SecurityErrorHandler;
import com.mobigen.aiop.nttpoc.module.auth.sse.SseSessionRegistry;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtCookieAuthFilter")
class JwtCookieAuthFilterTest {

    @Mock JwtProvider jwtProvider;
    @Mock SecurityErrorHandler securityErrorHandler;
    @Mock SseSessionRegistry sseSessionRegistry;
    @InjectMocks JwtCookieAuthFilter filter;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    private void givenCookie(String name, String value) {
        given(request.getCookies()).willReturn(new Cookie[]{new Cookie(name, value)});
    }

    @Nested
    @DisplayName("shouldNotFilter")
    class ShouldNotFilter {

        @Test
        @DisplayName("공개 경로 → 필터 건너뜀")
        void publicPath_skipsFilter() {
            given(request.getRequestURI()).willReturn("/api/auth/login");
            assertThat(filter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("보호 경로 → 필터 적용")
        void protectedPath_appliesFilter() {
            given(request.getRequestURI()).willReturn("/api/user/profile");
            assertThat(filter.shouldNotFilter(request)).isFalse();
        }
    }

    @Nested
    @DisplayName("doFilterInternal")
    class DoFilterInternal {

        @Test
        @DisplayName("쿠키 없음 → 다음 필터로 전달, SecurityContext 비어있음")
        void noCookie_passesToChain() throws Exception {
            given(request.getCookies()).willReturn(null);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("유효한 AT → SecurityContext에 인증 설정 후 다음 필터")
        void validToken_setsAuthentication() throws Exception {
            givenCookie(JwtCookieUtil.ACCESS_COOKIE, "valid-token");

            Claims claims = mock(Claims.class);
            given(claims.getIssuedAt()).willReturn(Date.from(Instant.now().minusSeconds(60)));
            given(jwtProvider.parseAndValidate("valid-token", JwtProvider.TYP_ACCESS)).willReturn(claims);
            given(jwtProvider.getSubject(claims)).willReturn("user-123");
            given(sseSessionRegistry.isRevokedAfter(anyString(), any(Instant.class))).willReturn(false);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                    .isEqualTo("user-123");
        }

        @Test
        @DisplayName("만료된 AT → TOKEN_EXPIRED 에러 응답, 다음 필터 호출 안됨")
        void expiredToken_writesTokenExpiredError() throws Exception {
            givenCookie(JwtCookieUtil.ACCESS_COOKIE, "expired-token");
            given(jwtProvider.parseAndValidate("expired-token", JwtProvider.TYP_ACCESS))
                    .willThrow(mock(ExpiredJwtException.class));

            filter.doFilterInternal(request, response, chain);

            verify(securityErrorHandler).writeError(request, response, ErrorCode.TOKEN_EXPIRED);
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("위변조 AT → TOKEN_INVALID 에러 응답")
        void tamperedToken_writesTokenInvalidError() throws Exception {
            givenCookie(JwtCookieUtil.ACCESS_COOKIE, "tampered-token");
            given(jwtProvider.parseAndValidate("tampered-token", JwtProvider.TYP_ACCESS))
                    .willThrow(mock(JwtException.class));

            filter.doFilterInternal(request, response, chain);

            verify(securityErrorHandler).writeError(request, response, ErrorCode.TOKEN_INVALID);
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("revoke된 세션 → TOKEN_INVALID 에러 응답")
        void revokedSession_writesTokenInvalidError() throws Exception {
            givenCookie(JwtCookieUtil.ACCESS_COOKIE, "valid-token");

            Claims claims = mock(Claims.class);
            given(claims.getIssuedAt()).willReturn(Date.from(Instant.now().minusSeconds(60)));
            given(jwtProvider.parseAndValidate("valid-token", JwtProvider.TYP_ACCESS)).willReturn(claims);
            given(jwtProvider.getSubject(claims)).willReturn("user-123");
            given(sseSessionRegistry.isRevokedAfter(anyString(), any(Instant.class))).willReturn(true);

            filter.doFilterInternal(request, response, chain);

            verify(securityErrorHandler).writeError(request, response, ErrorCode.TOKEN_INVALID);
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("이미 인증된 상태 → 토큰 검증 없이 다음 필터로")
        void alreadyAuthenticated_passesThrough() throws Exception {
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "user", null,
                            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(jwtProvider, never()).parseAndValidate(anyString(), anyString());
        }
    }
}
