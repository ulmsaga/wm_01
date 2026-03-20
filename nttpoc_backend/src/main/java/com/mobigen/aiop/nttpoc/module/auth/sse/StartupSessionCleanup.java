package com.mobigen.aiop.nttpoc.module.auth.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.mobigen.aiop.nttpoc.module.auth.dao.RefreshTokenDao;

/**
 * 서버 재기동 시 기존 활성 RT를 일괄 무효화.
 * - AT 만료(5분) 후 브라우저가 refresh를 시도하면 RT revoked → 인터셉터가 /login 으로 이동
 * - SSE Layer(sseService.js)와 함께 동작하여 재기동 후 세션 잔류 문제를 방지
 */
@Component
public class StartupSessionCleanup {

    private static final Logger log = LoggerFactory.getLogger(StartupSessionCleanup.class);

    private final RefreshTokenDao refreshTokenDao;

    public StartupSessionCleanup(RefreshTokenDao refreshTokenDao) {
        this.refreshTokenDao = refreshTokenDao;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void revokeAllOnStartup() {
        int count = refreshTokenDao.revokeAllActiveSessions();
        if (count > 0) {
            log.info("[Startup] 서버 재기동으로 인해 활성 세션 {}건을 무효화했습니다.", count);
        }
    }
}
