package com.mobigen.aiop.nttpoc.module.auth.sse;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * SSE 세션 레지스트리.
 * - userId → List<SseEmitter> 매핑 유지
 * - 중복 로그인 revoke 시각을 Caffeine 캐시에 기록 → SSE 재연결 시 즉시 감지
 * - 25초 heartbeat로 proxy 타임아웃 방지
 */
@Component
public class SseSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseSessionRegistry.class);

    /** userId → 활성 SseEmitter 목록 */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> sessions =
            new ConcurrentHashMap<>();

    /**
     * 세션 revoke 시각 캐시 (userId → revokedAt).
     * TTL = AT 만료(5분) + 여유(5분) = 10분.
     * AT 만료 후에는 SSE 재연결 자체가 불가하므로 10분이면 충분.
     */
    private final Cache<String, Instant> revokedSessions = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    // ── 등록 / 제거 ─────────────────────────────────────────────────────────

    public void register(String userId, SseEmitter emitter) {
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
        log.debug("[SSE] 등록 userId={}, 활성 emitter={}", userId, sessions.get(userId).size());
    }

    public void remove(String userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = sessions.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) sessions.remove(userId, list);
        }
    }

    // ── 이벤트 전송 ──────────────────────────────────────────────────────────

    /**
     * 해당 userId의 모든 활성 emitter에 이벤트 전송.
     * 전송 실패한 emitter는 자동으로 레지스트리에서 제거.
     */
    public void send(String userId, String eventName, String data) {
        CopyOnWriteArrayList<SseEmitter> emitters = sessions.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("[SSE] 전송 대상 없음 userId={}", userId);
            return;
        }

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
                log.debug("[SSE] 이벤트 전송 userId={}, event={}", userId, eventName);
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        dead.forEach(e -> remove(userId, e));
    }

    // ── 세션 revoke 캐시 ─────────────────────────────────────────────────────

    /**
     * 중복 로그인으로 인한 세션 무효화 시각을 캐시에 기록.
     * SSE 재연결 시 {@link #isRevokedAfter} 로 확인.
     */
    public void markRevoked(String userId) {
        // JWT iat는 초(second) 단위로 저장됨 → truncate to seconds로 정밀도 맞춤
        // 같은 초 안에 발급된 신규 AT가 오탐으로 kick되는 것을 방지
        revokedSessions.put(userId, Instant.now().truncatedTo(ChronoUnit.SECONDS));
        log.debug("[SSE] 세션 revoke 기록 userId={}", userId);
    }

    /**
     * 이 AT가 발급된 이후 세션이 무효화됐는지 확인.
     *
     * @param userId      사용자 ID
     * @param atIssuedAt  AT 발급 시각 (JWT iat)
     * @return true → 이 세션은 무효화됨 → SESSION_INVALIDATED 전송 필요
     */
    public boolean isRevokedAfter(String userId, Instant atIssuedAt) {
        Instant revokedAt = revokedSessions.getIfPresent(userId);
        return revokedAt != null && revokedAt.isAfter(atIssuedAt);
    }

    // ── Heartbeat ────────────────────────────────────────────────────────────

    /** 25초마다 전체 emitter에 heartbeat 전송 → Nginx/ALB 타임아웃 방지 */
    @Scheduled(fixedRate = 25_000, initialDelay = 25_000)
    @SuppressWarnings("UseSpecificCatch")
    public void heartbeat() {
        if (sessions.isEmpty()) return;

        sessions.forEach((userId, emitters) -> {
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data(""));
                } catch (Exception e) {
                    dead.add(emitter);
                }
            }
            dead.forEach(e -> remove(userId, e));
        });
    }
}
