package com.mobigen.aiop.nttpoc.module.auth.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtCookieUtil")
class JwtCookieUtilTest {

    private final JwtCookieUtil cookieUtil = new JwtCookieUtil(false, "Lax");
    private final JwtCookieUtil secureCookieUtil = new JwtCookieUtil(true, "Strict");

    @Nested
    @DisplayName("accessCookie")
    class AccessCookie {

        @Test
        @DisplayName("이름=access_token, httpOnly=true, path=/")
        void accessCookie_attributes() {
            ResponseCookie cookie = cookieUtil.accessCookie("token-value", 300);
            assertThat(cookie.getName()).isEqualTo(JwtCookieUtil.ACCESS_COOKIE);
            assertThat(cookie.getValue()).isEqualTo("token-value");
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(300);
        }

        @Test
        @DisplayName("secure=false → Secure 속성 없음")
        void accessCookie_insecure() {
            ResponseCookie cookie = cookieUtil.accessCookie("t", 300);
            assertThat(cookie.isSecure()).isFalse();
        }

        @Test
        @DisplayName("secure=true → Secure 속성 포함")
        void accessCookie_secure() {
            ResponseCookie cookie = secureCookieUtil.accessCookie("t", 300);
            assertThat(cookie.isSecure()).isTrue();
        }

        @Test
        @DisplayName("SameSite=Lax 적용")
        void accessCookie_sameSite() {
            ResponseCookie cookie = cookieUtil.accessCookie("t", 300);
            assertThat(cookie.getSameSite()).isEqualTo("Lax");
        }
    }

    @Nested
    @DisplayName("refreshCookie")
    class RefreshCookie {

        @Test
        @DisplayName("이름=refresh_token")
        void refreshCookie_name() {
            ResponseCookie cookie = cookieUtil.refreshCookie("rt-value", 2592000);
            assertThat(cookie.getName()).isEqualTo(JwtCookieUtil.REFRESH_COOKIE);
            assertThat(cookie.getValue()).isEqualTo("rt-value");
        }
    }

    @Nested
    @DisplayName("deleteCookie")
    class DeleteCookie {

        @Test
        @DisplayName("deleteAccessCookie → maxAge=0, value 빈문자열")
        void deleteAccessCookie_maxAgeZero() {
            ResponseCookie cookie = cookieUtil.deleteAccessCookie();
            assertThat(cookie.getName()).isEqualTo(JwtCookieUtil.ACCESS_COOKIE);
            assertThat(cookie.getValue()).isEqualTo("");
            assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(0);
        }

        @Test
        @DisplayName("deleteRefreshCookie → maxAge=0, value 빈문자열")
        void deleteRefreshCookie_maxAgeZero() {
            ResponseCookie cookie = cookieUtil.deleteRefreshCookie();
            assertThat(cookie.getName()).isEqualTo(JwtCookieUtil.REFRESH_COOKIE);
            assertThat(cookie.getValue()).isEqualTo("");
            assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(0);
        }
    }
}
