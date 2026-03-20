package com.mobigen.aiop.nttpoc.module.auth.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.mobigen.aiop.nttpoc.core.exception.ErrorCode;
import com.mobigen.aiop.nttpoc.core.exception.NttpocException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.mobigen.aiop.nttpoc.core.exception.ErrorCode;
import com.mobigen.aiop.nttpoc.core.exception.GlobalExceptionHandler;
import com.mobigen.aiop.nttpoc.core.exception.NttpocException;
import com.mobigen.aiop.nttpoc.core.util.RsaKeyUtil;
import com.mobigen.aiop.nttpoc.module.auth.jwt.JwtCookieUtil;
import com.mobigen.aiop.nttpoc.module.auth.jwt.JwtProvider;
import com.mobigen.aiop.nttpoc.module.auth.service.AuthService;
import com.mobigen.aiop.nttpoc.module.auth.service.OtpService;
import com.mobigen.aiop.nttpoc.module.auth.service.RefreshTokenService;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Mock AuthService authService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock OtpService otpService;
    @Mock RsaKeyUtil rsaKeyUtil;
    @Mock JwtCookieUtil jwtCookieUtil;
    @Mock MessageSource messageSource;
    @InjectMocks AuthController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GlobalExceptionHandler exHandler = new GlobalExceptionHandler(messageSource);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter p) {
                        return Authentication.class.isAssignableFrom(p.getParameterType());
                    }
                    @Override
                    public Object resolveArgument(MethodParameter p, ModelAndViewContainer mvc,
                            NativeWebRequest req, WebDataBinderFactory binder) {
                        return SecurityContextHolder.getContext().getAuthentication();
                    }
                })
                .setControllerAdvice(exHandler)
                .build();
        SecurityContextHolder.clearContext();
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("에러");
    }

    private JwtProvider.TokenPair fakePair() {
        return new JwtProvider.TokenPair("at", "rt", "ajti", "rjti",
                Instant.now().plusSeconds(300), Instant.now().plusSeconds(2592000));
    }

    private void givenDeleteCookies() {
        given(jwtCookieUtil.deleteAccessCookie())
                .willReturn(ResponseCookie.from("access_token", "").maxAge(0).build());
        given(jwtCookieUtil.deleteRefreshCookie())
                .willReturn(ResponseCookie.from("refresh_token", "").maxAge(0).build());
    }

    private void givenSetCookies() {
        given(jwtCookieUtil.accessCookie(anyString(), anyLong()))
                .willReturn(ResponseCookie.from("access_token", "at").build());
        given(jwtCookieUtil.refreshCookie(anyString(), anyLong()))
                .willReturn(ResponseCookie.from("refresh_token", "rt").build());
    }

    @Nested
    @DisplayName("GET /api/auth/rsa-public-key")
    class GetRsaPublicKey {

        @Test
        @DisplayName("정상 요청 → 200 + keyId/publicKey 포함")
        void success_returns200() throws Exception {
            given(rsaKeyUtil.generateKeyPair())
                    .willReturn(new RsaKeyUtil.RsaPublicKeyInfo("key-123", "base64key"));

            mockMvc.perform(get("/api/auth/rsa-public-key"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.keyId").value("key-123"));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("2차 인증 필요 → tokenPair=null, OTP 정보 반환")
        void requiresOtp_returnsOtpInfo() throws Exception {
            given(authService.login(any(), any(), any()))
                    .willReturn(new AuthService.LoginResult(
                            Map.of("requireSecondAuth", true, "otpSeq", 1L), null));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"keyId\":\"k\",\"encryptedCredentials\":\"e\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.requireSecondAuth").value(true));
        }

        @Test
        @DisplayName("2차 인증 불필요 → JWT 쿠키 설정 + 200")
        void noOtp_setsTokenCookiesAndReturns200() throws Exception {
            givenSetCookies();
            given(authService.login(any(), any(), any()))
                    .willReturn(new AuthService.LoginResult(
                            Map.of("userSeq", 1L, "userName", "홍길동"), fakePair()));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"keyId\":\"k\",\"encryptedCredentials\":\"e\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userSeq").value(1));
        }

        @Test
        @DisplayName("NttpocException(LOGIN_FAILED) → 401")
        void loginFailed_returns401() throws Exception {
            given(authService.login(any(), any(), any()))
                    .willThrow(new NttpocException(ErrorCode.LOGIN_FAILED));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"keyId\":\"k\",\"encryptedCredentials\":\"e\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/verify-otp")
    class VerifyOtp {

        @Test
        @DisplayName("OTP 인증 성공 → 200 + 사용자 정보")
        void success_returns200() throws Exception {
            givenSetCookies();
            given(otpService.verifyOtp(1L, "123456")).willReturn(100L);
            given(refreshTokenService.loginAndIssueTokens(anyLong(), any(), any())).willReturn(fakePair());
            given(authService.getMe("100")).willReturn(Map.of("authenticated", true, "userName", "홍길동"));

            mockMvc.perform(post("/api/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"otpSeq\":1,\"otpCode\":\"123456\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("요청 바디 필드 누락 (null) → 400")
        void missingFields_returns400() throws Exception {
            mockMvc.perform(post("/api/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }

        @Test
        @DisplayName("OTP 불일치 → 401")
        void otpMismatch_returns401() throws Exception {
            given(otpService.verifyOtp(anyLong(), anyString()))
                    .willThrow(new NttpocException(ErrorCode.OTP_MISMATCH, 2));

            mockMvc.perform(post("/api/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"otpSeq\":1,\"otpCode\":\"000000\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("OTP_MISMATCH"));
        }
    }

    @Nested
    @DisplayName("GET /api/auth/me")
    class Me {

        @Test
        @DisplayName("인증된 사용자 → 200 + 사용자 정보")
        void authenticated_returnsUserInfo() throws Exception {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "100", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            given(authService.getMe("100"))
                    .willReturn(Map.of("authenticated", true, "userName", "홍길동"));

            mockMvc.perform(get("/api/auth/me").principal(auth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.authenticated").value(true));
        }

        @Test
        @DisplayName("미인증 → NttpocException UNAUTHORIZED → 401")
        void unauthenticated_throws401() throws Exception {
            SecurityContextHolder.clearContext();

            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class Refresh {

        @Test
        @DisplayName("refresh_token 쿠키 없음 → 401")
        void noRefreshCookie_returns401() throws Exception {
            mockMvc.perform(post("/api/auth/refresh"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("만료된 RT → TOKEN_EXPIRED")
        void expiredToken_returnsTokenExpired() throws Exception {
            given(refreshTokenService.refreshRotate(anyString(), any(), any()))
                    .willThrow(mock(ExpiredJwtException.class));

            mockMvc.perform(post("/api/auth/refresh")
                            .cookie(new Cookie("refresh_token", "expired-rt")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        }

        @Test
        @DisplayName("RT 재사용 감지 → UNAUTHORIZED")
        void tokenReuse_returnsUnauthorized() throws Exception {
            given(refreshTokenService.refreshRotate(anyString(), any(), any()))
                    .willThrow(new NttpocException(ErrorCode.UNAUTHORIZED));

            mockMvc.perform(post("/api/auth/refresh")
                            .cookie(new Cookie("refresh_token", "reused-rt")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("정상 refresh → 200")
        void success_returns200() throws Exception {
            givenSetCookies();
            given(refreshTokenService.refreshRotate(anyString(), any(), any())).willReturn(fakePair());

            mockMvc.perform(post("/api/auth/refresh")
                            .cookie(new Cookie("refresh_token", "valid-rt")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/logout")
    class Logout {

        @Test
        @DisplayName("로그아웃 → 200 + 쿠키 삭제")
        void logout_returns200() throws Exception {
            givenDeleteCookies();

            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
