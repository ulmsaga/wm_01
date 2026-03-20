package com.mobigen.aiop.nttpoc.module.auth.sse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("SseSessionRegistry")
class SseSessionRegistryTest {

    private SseSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseSessionRegistry();
    }

    @Nested
    @DisplayName("register / remove")
    class RegisterRemove {

        @Test
        @DisplayName("register 후 send → emitter.send 호출됨")
        void register_thenSend_callsEmitter() throws Exception {
            SseEmitter emitter = mock(SseEmitter.class);
            registry.register("u1", emitter);

            registry.send("u1", "TEST_EVENT", "data");

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("remove 후 send → emitter.send 호출 안됨")
        void remove_thenSend_doesNotCallEmitter() throws Exception {
            SseEmitter emitter = mock(SseEmitter.class);
            registry.register("u1", emitter);
            registry.remove("u1", emitter);

            registry.send("u1", "TEST_EVENT", "data");

            verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("미등록 userId remove → 예외 없이 무시")
        void remove_unknownUser_noException() {
            SseEmitter emitter = mock(SseEmitter.class);
            assertThatCode(() -> registry.remove("unknown", emitter))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("등록된 emitter 없을 때 send → 예외 없이 무시")
        void send_noEmitters_noException() {
            assertThatCode(() -> registry.send("nobody", "EVT", "data"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("send 중 IOException → 실패한 emitter 자동 제거")
        void send_ioException_removesDeadEmitter() throws Exception {
            SseEmitter deadEmitter = mock(SseEmitter.class);
            doThrow(new java.io.IOException("broken pipe"))
                    .when(deadEmitter).send(any(SseEmitter.SseEventBuilder.class));

            registry.register("u1", deadEmitter);
            registry.send("u1", "EVT", "data"); // IOException 발생 → 제거

            // 다음 send에서 더 이상 호출 안됨
            registry.send("u1", "EVT", "data");
            verify(deadEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Nested
    @DisplayName("markRevoked / isRevokedAfter")
    class RevokeCache {

        @Test
        @DisplayName("markRevoked 후 이전 시각으로 isRevokedAfter → true")
        void markRevoked_isRevokedAfterOldInstant_true() {
            Instant before = Instant.now().minusSeconds(5);
            registry.markRevoked("u1");

            assertThat(registry.isRevokedAfter("u1", before)).isTrue();
        }

        @Test
        @DisplayName("markRevoked 후 미래 시각으로 isRevokedAfter → false")
        void markRevoked_isRevokedAfterFutureInstant_false() {
            registry.markRevoked("u1");
            Instant future = Instant.now().plusSeconds(60);

            assertThat(registry.isRevokedAfter("u1", future)).isFalse();
        }

        @Test
        @DisplayName("markRevoked 없이 isRevokedAfter → false")
        void noRevoke_isRevokedAfter_false() {
            assertThat(registry.isRevokedAfter("u1", Instant.now().minusSeconds(10))).isFalse();
        }
    }

    @Nested
    @DisplayName("heartbeat")
    class Heartbeat {

        @Test
        @DisplayName("등록된 emitter 없을 때 heartbeat → 예외 없이 종료")
        void heartbeat_noEmitters_noException() {
            assertThatCode(() -> registry.heartbeat()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("heartbeat 시 IOException → dead emitter 제거 후 계속")
        void heartbeat_ioException_removesDead() throws Exception {
            SseEmitter dead = mock(SseEmitter.class);
            doThrow(new java.io.IOException()).when(dead).send(any(SseEmitter.SseEventBuilder.class));
            registry.register("u1", dead);

            assertThatCode(() -> registry.heartbeat()).doesNotThrowAnyException();
        }
    }
}
