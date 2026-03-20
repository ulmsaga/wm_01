package com.mobigen.aiop.nttpoc.module.auth.service;

import com.mobigen.aiop.nttpoc.core.exception.ErrorCode;
import com.mobigen.aiop.nttpoc.core.exception.NttpocException;
import com.mobigen.aiop.nttpoc.module.auth.dao.RefreshTokenDao;
import com.mobigen.aiop.nttpoc.module.auth.jwt.JwtProvider;
import com.mobigen.aiop.nttpoc.module.auth.service.impl.RefreshTokenServiceImpl;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenServiceImpl")
class RefreshTokenServiceImplTest {

    @Mock RefreshTokenDao refreshTokenDao;
    @Mock JwtProvider jwtProvider;
    @InjectMocks RefreshTokenServiceImpl service;

    private JwtProvider.TokenPair fakePair(String userId) {
        return new JwtProvider.TokenPair(
                "access-token", "refresh-token",
                "access-jti-" + userId, "refresh-jti-" + userId,
                Instant.now().plusSeconds(300),
                Instant.now().plusSeconds(2592000));
    }

    @Nested
    @DisplayName("loginAndIssueTokens")
    class LoginAndIssueTokens {

        @Test
        @DisplayName("토큰 발급 후 refreshTokenDao.insert 호출, TokenPair 반환")
        void issuesTokenAndInsertsToDb() {
            JwtProvider.TokenPair pair = fakePair("1");
            given(jwtProvider.issueTokenPair(eq("1"), any())).willReturn(pair);

            JwtProvider.TokenPair result = service.loginAndIssueTokens(1L, "agent", "127.0.0.1");

            assertThat(result).isSameAs(pair);
            verify(refreshTokenDao).insert(any());
        }
    }

    @Nested
    @DisplayName("refreshRotate")
    class RefreshRotate {

        @Test
        @DisplayName("정상 로테이션 → 신규 TokenPair 반환 및 DB insert")
        void validRotation_returnsNewPair() {
            Claims claims = mock(Claims.class);
            given(jwtProvider.parseAndValidate("old-rt", JwtProvider.TYP_REFRESH)).willReturn(claims);
            given(jwtProvider.getSubject(claims)).willReturn("1");
            given(jwtProvider.getJti(claims)).willReturn("old-jti");

            Map<String, Object> row = new HashMap<>();
            row.put("revoked_at", null);
            given(refreshTokenDao.selectByJtiHash(any())).willReturn(row);
            given(refreshTokenDao.rotate(any())).willReturn(1);

            JwtProvider.TokenPair newPair = fakePair("new");
            given(jwtProvider.issueTokenPair(eq("1"), any())).willReturn(newPair);

            JwtProvider.TokenPair result = service.refreshRotate("old-rt", "agent", "127.0.0.1");

            assertThat(result).isSameAs(newPair);
            verify(refreshTokenDao).insert(any());
        }

        @Test
        @DisplayName("알 수 없는 RT (DB에 없음) → NttpocException(TOKEN_INVALID)")
        void unknownToken_throwsTokenInvalid() {
            Claims claims = mock(Claims.class);
            given(jwtProvider.parseAndValidate("bad-rt", JwtProvider.TYP_REFRESH)).willReturn(claims);
            given(jwtProvider.getSubject(claims)).willReturn("1");
            given(jwtProvider.getJti(claims)).willReturn("bad-jti");
            given(refreshTokenDao.selectByJtiHash(any())).willReturn(null);

            assertThatThrownBy(() -> service.refreshRotate("bad-rt", "agent", "127.0.0.1"))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_INVALID);
        }

        @Test
        @DisplayName("이미 revoke된 RT 재사용 → revokeAll 후 NttpocException(UNAUTHORIZED)")
        void reuseDetected_revokesAllAndThrows() {
            Claims claims = mock(Claims.class);
            given(jwtProvider.parseAndValidate("reused-rt", JwtProvider.TYP_REFRESH)).willReturn(claims);
            given(jwtProvider.getSubject(claims)).willReturn("1");
            given(jwtProvider.getJti(claims)).willReturn("reused-jti");

            Map<String, Object> row = new HashMap<>();
            row.put("revoked_at", Instant.now());
            given(refreshTokenDao.selectByJtiHash(any())).willReturn(row);

            assertThatThrownBy(() -> service.refreshRotate("reused-rt", "agent", "127.0.0.1"))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED);

            verify(refreshTokenDao).revokeAllByUserSeq(any());
        }

        @Test
        @DisplayName("rotate 실패 (updated != 1) → revokeAll 후 NttpocException(TOKEN_INVALID)")
        void rotationFailed_revokesAllAndThrows() {
            Claims claims = mock(Claims.class);
            given(jwtProvider.parseAndValidate("rt", JwtProvider.TYP_REFRESH)).willReturn(claims);
            given(jwtProvider.getSubject(claims)).willReturn("1");
            given(jwtProvider.getJti(claims)).willReturn("jti");

            Map<String, Object> row = new HashMap<>();
            row.put("revoked_at", null);
            given(refreshTokenDao.selectByJtiHash(any())).willReturn(row);
            given(refreshTokenDao.rotate(any())).willReturn(0); // 실패

            JwtProvider.TokenPair newPair = fakePair("new");
            given(jwtProvider.issueTokenPair(any(), any())).willReturn(newPair);

            assertThatThrownBy(() -> service.refreshRotate("rt", "agent", "127.0.0.1"))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_INVALID);

            verify(refreshTokenDao).revokeAllByUserSeq(any());
        }
    }

    @Nested
    @DisplayName("revokeAllSessions / hasActiveSessions")
    class SessionManagement {

        @Test
        @DisplayName("revokeAllSessions → refreshTokenDao.revokeAllByUserSeq 호출, 삭제 건수 반환")
        void revokeAllSessions_callsDao() {
            given(refreshTokenDao.revokeAllByUserSeq(any())).willReturn(3);

            int count = service.revokeAllSessions(1L);

            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("hasActiveSessions → countActiveSessions > 0 이면 true")
        void hasActiveSessions_true() {
            given(refreshTokenDao.countActiveSessions(any())).willReturn(2);
            assertThat(service.hasActiveSessions(1L)).isTrue();
        }

        @Test
        @DisplayName("hasActiveSessions → countActiveSessions = 0 이면 false")
        void hasActiveSessions_false() {
            given(refreshTokenDao.countActiveSessions(any())).willReturn(0);
            assertThat(service.hasActiveSessions(1L)).isFalse();
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("유효한 RT → revokeByJtiHash 호출")
        void validToken_revokes() {
            Claims claims = mock(Claims.class);
            given(jwtProvider.parseAndValidate("valid-rt", JwtProvider.TYP_REFRESH)).willReturn(claims);
            given(jwtProvider.getJti(claims)).willReturn("jti-value");

            service.logout("valid-rt");

            verify(refreshTokenDao).revokeByJtiHash(any());
        }

        @Test
        @DisplayName("null RT → DAO 호출 없이 종료")
        void nullToken_noOp() {
            service.logout(null);
            verify(refreshTokenDao, never()).revokeByJtiHash(any());
        }

        @Test
        @DisplayName("빈 문자열 RT → DAO 호출 없이 종료")
        void blankToken_noOp() {
            service.logout("   ");
            verify(refreshTokenDao, never()).revokeByJtiHash(any());
        }
    }
}
