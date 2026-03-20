package com.mobigen.aiop.nttpoc.module.auth.scheduler;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mobigen.aiop.nttpoc.module.auth.dao.RefreshTokenDao;

/**
 * 만료된 Refresh Token 일괄 삭제 스케줄러.
 * - 매일 새벽 4시 실행 (app.jwt.cleanup.cron 으로 변경 가능)
 * - expires_at < 현재시각 인 레코드 전체 삭제 (revoked_at 여부 무관)
 */
@Component
public class RefreshTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);

    private final RefreshTokenDao refreshTokenDao;

    public RefreshTokenCleanupScheduler(RefreshTokenDao refreshTokenDao) {
        this.refreshTokenDao = refreshTokenDao;
    }

    @Scheduled(cron = "${app.jwt.cleanup.cron:0 0 4 * * *}")
    public void deleteExpiredTokens() {
        log.info("[Cleanup] ─── Refresh Token 만료 레코드 정리 시작 ───────────────");

        long start = System.currentTimeMillis();
        Timestamp now = Timestamp.from(Instant.now());

        int deleted;
        try {
            deleted = refreshTokenDao.deleteExpired(Map.of("now", now));
        } catch (Exception e) {
            log.error("[Cleanup] 정리 중 오류 발생 — 처리 중단", e);
            return;
        }

        long elapsed = System.currentTimeMillis() - start;

        if (deleted > 0) {
            log.info("[Cleanup] 삭제 완료: {}건  소요시간: {}ms", deleted, elapsed);
        } else {
            log.info("[Cleanup] 삭제 대상 없음 (모든 토큰이 유효하거나 이미 정리됨)  소요시간: {}ms", elapsed);
        }

        log.info("[Cleanup] ─── Refresh Token 만료 레코드 정리 종료 ───────────────");
    }
}
