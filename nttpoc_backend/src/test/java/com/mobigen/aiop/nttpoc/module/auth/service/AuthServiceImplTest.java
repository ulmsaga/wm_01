package com.mobigen.aiop.nttpoc.module.auth.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import com.mobigen.aiop.nttpoc.core.exception.ErrorCode;
import com.mobigen.aiop.nttpoc.core.exception.NttpocException;
import com.mobigen.aiop.nttpoc.core.util.PasswordUtil;
import com.mobigen.aiop.nttpoc.core.util.RsaKeyUtil;
import com.mobigen.aiop.nttpoc.module.auth.dao.AuthDao;
import com.mobigen.aiop.nttpoc.module.auth.dto.LoginRequest;
import com.mobigen.aiop.nttpoc.module.auth.jwt.JwtProvider;
import com.mobigen.aiop.nttpoc.module.auth.service.impl.AuthServiceImpl;
import com.mobigen.aiop.nttpoc.module.auth.sse.SseSessionRegistry;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl")
class AuthServiceImplTest {

    @Mock AuthDao authDao;
    @Mock RsaKeyUtil rsaKeyUtil;
    @Mock PasswordUtil passwordUtil;
    @Mock RefreshTokenService refreshTokenService;
    @Mock OtpService otpService;
    @Mock tools.jackson.databind.ObjectMapper objectMapper;
    @Mock SseSessionRegistry sseSessionRegistry;
    @Mock MessageSource messageSource;
    @InjectMocks AuthServiceImpl authService;

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    private LoginRequest request(boolean forceLogin) {
        LoginRequest req = mock(LoginRequest.class);
        given(req.getKeyId()).willReturn("key-id");
        given(req.getEncryptedCredentials()).willReturn("encrypted");
        lenient().when(req.isForceLogin()).thenReturn(forceLogin);
        return req;
    }

    private Map<String, Object> activeUser() {
        Map<String, Object> user = new HashMap<>();
        user.put("USER_STATUS", "ACTIVE");
        user.put("AUTH_STATUS", "ACTIVE");
        user.put("USER_AUTH_SEQ", 1L);
        user.put("USER_SEQ", 100L);
        user.put("PASSWORD_HASH", "hashed");
        user.put("ALLOW_DUPLICATE_LOGIN", "Y");
        user.put("REQUIRE_SECOND_AUTH", "N");
        user.put("USER_ID", "uid-001");
        user.put("USER_NAME", "홍길동");
        user.put("FAILED_LOGIN_COUNT", 0);
        return user;
    }

    private void mockDecryptToJson(String loginId, String password) throws Exception {
        String json = "{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}";
        given(rsaKeyUtil.decrypt(anyString(), anyString())).willReturn(json);
        tools.jackson.databind.JsonNode node = mock(tools.jackson.databind.JsonNode.class);
        tools.jackson.databind.JsonNode loginIdNode = mock(tools.jackson.databind.JsonNode.class);
        tools.jackson.databind.JsonNode passwordNode = mock(tools.jackson.databind.JsonNode.class);
        given(objectMapper.readTree(json)).willReturn(node);
        given(node.get("loginId")).willReturn(loginIdNode);
        given(node.get("password")).willReturn(passwordNode);
        given(loginIdNode.asText()).willReturn(loginId);
        given(passwordNode.asText()).willReturn(password);
    }

    // ── login ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("JSON 파싱 실패 → INVALID_CREDENTIALS_FORMAT 예외")
        void jsonParseError_throwsInvalidCredentialsFormat() throws Exception {
            given(rsaKeyUtil.decrypt(anyString(), anyString())).willReturn("bad-json");
            given(objectMapper.readTree("bad-json"))
                    .willThrow(mock(tools.jackson.core.JacksonException.class));

            assertThatThrownBy(() -> authService.login(request(false), "agent", "127.0.0.1"))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS_FORMAT);
        }

        @Test
        @DisplayName("사용자 없음 → LOGIN_FAILED 예외")
        void userNotFound_throwsLoginFailed() throws Exception {
            mockDecryptToJson("nobody", "pass");
            given(authDao.selectUserForLogin(any())).willReturn(null);

            assertThatThrownBy(() -> authService.login(request(false), "agent", "127.0.0.1"))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.LOGIN_FAILED);
        }

        @Test
        @DisplayName("USER_STATUS != ACTIVE → ACCOUNT_INACTIVE 예외")
        void inactiveUser_throwsAccountInactive() throws Exception {
            mockDecryptToJson("user", "pass");
            Map<String, Object> user = activeUser();
            user.put("USER_STATUS", "INACTIVE");
            given(authDao.selectUserForLogin(any())).willReturn(user);

            assertThatThrownBy(() -> authService.login(request(false), "agent", "127.0.0.1"))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
        }

        @Test
        @DisplayName("AUTH_STATUS != ACTIVE → AUTH_LOCKED 예외")
        void lockedAuth_throwsAuthLocked() throws Exception {
            mockDecryptToJson("user", "pass");
            Map<String, Object> user = activeUser();
            user.put("AUTH_STATUS", "LOCKED");
            given(authDao.selectUserForLogin(any())).willReturn(user);

            assertThatThrownBy(() -> authService.login(request(false), "agent", "127.0.0.1"))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_LOCKED);
        }

        @Test
        @DisplayName("비밀번호 불일치 → LOGIN_FAILED + loginFailure 업데이트")
        void wrongPassword_throwsLoginFailed() throws Exception {
            mockDecryptToJson("user", "wrong");
            given(authDao.selectUserForLogin(any())).willReturn(activeUser());
            given(passwordUtil.matches("wrong", "hashed")).willReturn(false);

            assertThatThrownBy(() -> authService.login(request(false), "agent", "127.0.0.1"))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.LOGIN_FAILED);
            verify(authDao).updateLoginFailure(any());
        }

        @Test
        @DisplayName("비밀번호 불일치 + 실패 횟수 임계값(5) 도달 → AUTH_LOCKED")
        void wrongPassword_maxFailCount_throwsAuthLocked() throws Exception {
            mockDecryptToJson("user", "wrong");
            Map<String, Object> user = activeUser();
            user.put("FAILED_LOGIN_COUNT", 4); // 4 + 1 = 5 → lock
            given(authDao.selectUserForLogin(any())).willReturn(user);
            given(passwordUtil.matches("wrong", "hashed")).willReturn(false);

            assertThatThrownBy(() -> authService.login(request(false), "agent", "127.0.0.1"))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_LOCKED);
            verify(authDao).updateLoginFailure(any());
        }

        @Test
        @DisplayName("중복 로그인 불허 + 기존 세션 있음 + forceLogin=false → requireDuplicateConfirm 반환")
        void duplicateLoginNotAllowed_requiresConfirm() throws Exception {
            mockDecryptToJson("user", "pass");
            Map<String, Object> user = activeUser();
            user.put("ALLOW_DUPLICATE_LOGIN", "N");
            given(authDao.selectUserForLogin(any())).willReturn(user);
            given(passwordUtil.matches(anyString(), anyString())).willReturn(true);
            given(refreshTokenService.hasActiveSessions(100L)).willReturn(true);
            given(messageSource.getMessage(anyString(), any(), any())).willReturn("이미 로그인된 계정입니다.");

            AuthService.LoginResult result = authService.login(request(false), "agent", "127.0.0.1");

            assertThat(result.response()).containsEntry("requireDuplicateConfirm", true);
            assertThat(result.tokenPair()).isNull();
        }

        @Test
        @DisplayName("2차 인증 필요 + EMAIL 있음 → requireSecondAuth + otpSeq 반환")
        void requireSecondAuth_email_returnsOtpSeq() throws Exception {
            mockDecryptToJson("user", "pass");
            Map<String, Object> user = activeUser();
            user.put("REQUIRE_SECOND_AUTH", "Y");
            user.put("EMAIL", "user@test.com");
            given(authDao.selectUserForLogin(any())).willReturn(user);
            given(passwordUtil.matches(anyString(), anyString())).willReturn(true);
            given(otpService.createAndSendOtp(anyLong(), anyString(), anyString())).willReturn(99L);

            AuthService.LoginResult result = authService.login(request(false), "agent", "127.0.0.1");

            assertThat(result.response()).containsEntry("requireSecondAuth", true);
            assertThat(result.response()).containsEntry("otpSeq", 99L);
            assertThat(result.tokenPair()).isNull();
        }

        @Test
        @DisplayName("2차 인증 필요 + EMAIL/Phone 없음 → NO_SECOND_AUTH_TARGET 예외")
        void requireSecondAuth_noTarget_throwsException() throws Exception {
            mockDecryptToJson("user", "pass");
            Map<String, Object> user = activeUser();
            user.put("REQUIRE_SECOND_AUTH", "Y");
            user.put("EMAIL", null);
            user.put("PHONE", null);
            given(authDao.selectUserForLogin(any())).willReturn(user);
            given(passwordUtil.matches(anyString(), anyString())).willReturn(true);

            assertThatThrownBy(() -> authService.login(request(false), "agent", "127.0.0.1"))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NO_SECOND_AUTH_TARGET);
        }

        @Test
        @DisplayName("2차 인증 불필요 → JWT 발급 후 LoginResult 반환")
        void noSecondAuth_issuesToken() throws Exception {
            mockDecryptToJson("user", "pass");
            given(authDao.selectUserForLogin(any())).willReturn(activeUser());
            given(passwordUtil.matches(anyString(), anyString())).willReturn(true);
            JwtProvider.TokenPair pair = new JwtProvider.TokenPair(
                    "at", "rt", "ajti", "rjti",
                    Instant.now().plusSeconds(300), Instant.now().plusSeconds(2592000));
            given(refreshTokenService.loginAndIssueTokens(anyLong(), anyString(), anyString()))
                    .willReturn(pair);

            AuthService.LoginResult result = authService.login(request(false), "agent", "127.0.0.1");

            assertThat(result.tokenPair()).isSameAs(pair);
            assertThat(result.response()).containsKey("userSeq");
        }
    }

    // ── getMe ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMe")
    class GetMe {

        @Test
        @DisplayName("사용자 있음 → authenticated=true + 사용자 정보")
        void userExists_returnsInfo() {
            Map<String, Object> user = new HashMap<>();
            user.put("USER_SEQ", 1L);
            user.put("USER_ID", "user01");
            user.put("USER_NAME", "홍길동");
            given(authDao.selectUserInfoByUserSeq(1L)).willReturn(user);

            Map<String, Object> result = authService.getMe("1");

            assertThat(result).containsEntry("authenticated", true)
                    .containsEntry("userId", "user01");
        }

        @Test
        @DisplayName("사용자 없음 → authenticated=false")
        void userNotFound_returnsNotAuthenticated() {
            given(authDao.selectUserInfoByUserSeq(99L)).willReturn(null);

            Map<String, Object> result = authService.getMe("99");

            assertThat(result).containsEntry("authenticated", false);
        }
    }

    // ── maskTarget (간접 검증) ────────────────────────────────────────────────

    @Nested
    @DisplayName("maskTarget (OTP 응답 sendTarget 마스킹)")
    class MaskTarget {

        @Test
        @DisplayName("EMAIL 2자 local → 2자 + *** 마스킹")
        void emailShortLocal_masking() throws Exception {
            mockDecryptToJson("user", "pass");
            Map<String, Object> user = activeUser();
            user.put("REQUIRE_SECOND_AUTH", "Y");
            user.put("EMAIL", "ab@test.com");
            given(authDao.selectUserForLogin(any())).willReturn(user);
            given(passwordUtil.matches(anyString(), anyString())).willReturn(true);
            given(otpService.createAndSendOtp(anyLong(), anyString(), anyString())).willReturn(1L);

            AuthService.LoginResult result = authService.login(request(false), "agent", "127.0.0.1");

            assertThat(result.response().get("sendTarget").toString()).isEqualTo("ab***@test.com");
        }

        @Test
        @DisplayName("EMAIL 4자 local → 앞 2자 + *** 마스킹")
        void emailLongLocal_masking() throws Exception {
            mockDecryptToJson("user", "pass");
            Map<String, Object> user = activeUser();
            user.put("REQUIRE_SECOND_AUTH", "Y");
            user.put("EMAIL", "abcd@test.com");
            given(authDao.selectUserForLogin(any())).willReturn(user);
            given(passwordUtil.matches(anyString(), anyString())).willReturn(true);
            given(otpService.createAndSendOtp(anyLong(), anyString(), anyString())).willReturn(1L);

            AuthService.LoginResult result = authService.login(request(false), "agent", "127.0.0.1");

            assertThat(result.response().get("sendTarget").toString()).isEqualTo("ab***@test.com");
        }
    }
}
