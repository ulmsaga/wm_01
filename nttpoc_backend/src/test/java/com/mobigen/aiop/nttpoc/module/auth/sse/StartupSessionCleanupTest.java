package com.mobigen.aiop.nttpoc.module.auth.sse;

import com.mobigen.aiop.nttpoc.module.auth.dao.RefreshTokenDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("StartupSessionCleanup")
class StartupSessionCleanupTest {

    @Mock RefreshTokenDao refreshTokenDao;
    @InjectMocks StartupSessionCleanup cleanup;

    @Nested
    @DisplayName("revokeAllOnStartup")
    class RevokeAllOnStartup {

        @Test
        @DisplayName("활성 세션 있음 → revoke 수행 후 로그 출력 (예외 없음)")
        void activeSessions_revoked() {
            given(refreshTokenDao.revokeAllActiveSessions()).willReturn(5);

            assertThatCode(() -> cleanup.revokeAllOnStartup()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("활성 세션 없음 → 로그 없이 정상 완료")
        void noActiveSessions_noLog() {
            given(refreshTokenDao.revokeAllActiveSessions()).willReturn(0);

            assertThatCode(() -> cleanup.revokeAllOnStartup()).doesNotThrowAnyException();
        }
    }
}
