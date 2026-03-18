package com.saga.wm.module.auth.controller;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.saga.wm.module.auth.sse.SseSessionRegistry;

import io.jsonwebtoken.Claims;

/**
 * SSE 세션 엔드포인트.
 * 로그인 후 프론트엔드가 연결을 유지하며 서버 push 이벤트를 수신한다.
 *
 * 3중 안전망:
 *  1) 중복 로그인 발생 즉시 SESSION_INVALIDATED push (emitter 살아있을 때)
 *  2) SSE 재연결 시 revoke 여부 체크 → 즉시 SESSION_INVALIDATED (emitter 죽었다 살아날 때)
 *  3) AT 만료(5분) → 재연결 자체가 불가 → 자연 로그아웃
 */
@RestController
@RequestMapping("/api/sse")
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    private final SseSessionRegistry registry;

    public SseController(SseSessionRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(value = "/session", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter session(Authentication auth) {
        String userId = auth.getName();

        // AT claims는 JwtCookieAuthFilter에서 details에 저장됨
        Claims claims = (Claims) auth.getDetails();
        Date issuedAt = claims.getIssuedAt();
        Instant atIssuedAt = issuedAt != null ? issuedAt.toInstant() : Instant.MIN;

        SseEmitter emitter = new SseEmitter(-1L); // 서버 측 timeout 없음 (heartbeat로 유지)

        // 재연결 시: 이미 revoke된 세션인지 확인 → 즉시 종료
        if (registry.isRevokedAfter(userId, atIssuedAt)) {
            log.debug("[SSE] 재연결 시 revoke 감지 → SESSION_INVALIDATED 즉시 전송 userId={}", userId);
            try {
                emitter.send(SseEmitter.event().name("SESSION_INVALIDATED").data("duplicate_login"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        // 정상 연결 → 레지스트리 등록
        registry.register(userId, emitter);

        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data("ok"));
            log.debug("[SSE] 연결 완료 userId={}", userId);
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }
}
