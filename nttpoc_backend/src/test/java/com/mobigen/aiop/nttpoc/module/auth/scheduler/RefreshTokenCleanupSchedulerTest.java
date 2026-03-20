package com.mobigen.aiop.nttpoc.module.auth.scheduler;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mobigen.aiop.nttpoc.module.auth.dao.RefreshTokenDao;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenCleanupScheduler")
class RefreshTokenCleanupSchedulerTest {

    @Mock RefreshTokenDao refreshTokenDao;
    @InjectMocks RefreshTokenCleanupScheduler scheduler;

    @Nested
    @DisplayName("deleteExpiredTokens")
    @SuppressWarnings("unused")
    class DeleteExpiredTokens {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("삭제 건수 > 0 → refreshTokenDao.deleteExpired 호출")
        void deleteExpiredTokens_deletedCountPositive() {
            given(refreshTokenDao.deleteExpired(any(Map.class))).willReturn(5);

            scheduler.deleteExpiredTokens();

            verify(refreshTokenDao).deleteExpired(any(Map.class));
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("삭제 건수 = 0 → refreshTokenDao.deleteExpired 호출")
        void deleteExpiredTokens_deletedCountZero() {
            given(refreshTokenDao.deleteExpired(any(Map.class))).willReturn(0);

            scheduler.deleteExpiredTokens();

            verify(refreshTokenDao).deleteExpired(any(Map.class));
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("DB 예외 발생 → 예외 전파 없이 종료")
        void deleteExpiredTokens_dbException_doesNotThrow() {
            given(refreshTokenDao.deleteExpired(any(Map.class)))
                    .willThrow(new RuntimeException("DB error"));

            // 예외가 외부로 전파되지 않아야 함
            org.assertj.core.api.Assertions.assertThatCode(() -> scheduler.deleteExpiredTokens())
                    .doesNotThrowAnyException();
        }
    }
}
