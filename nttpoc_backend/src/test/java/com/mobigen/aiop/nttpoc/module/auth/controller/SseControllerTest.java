package com.mobigen.aiop.nttpoc.module.auth.controller;

import com.mobigen.aiop.nttpoc.module.auth.sse.SseSessionRegistry;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SseController")
class SseControllerTest {

    @Mock SseSessionRegistry registry;
    @InjectMocks SseController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
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
                .build();
        SecurityContextHolder.clearContext();
    }

    private UsernamePasswordAuthenticationToken authWith(Claims claims) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "100", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(claims);
        return auth;
    }

    @Nested
    @DisplayName("GET /api/sse/session")
    class Session {

        @Test
        @DisplayName("정상 SSE 연결 → CONNECTED 이벤트 포함")
        void normalConnection_returnsConnectedEvent() throws Exception {
            Claims claims = mock(Claims.class);
            given(claims.getIssuedAt()).willReturn(Date.from(Instant.now().minusSeconds(60)));
            given(registry.isRevokedAfter(anyString(), any(Instant.class))).willReturn(false);

            mockMvc.perform(get("/api/sse/session").accept(MediaType.TEXT_EVENT_STREAM)
                    .principal(authWith(claims)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("CONNECTED")));
        }

        @Test
        @DisplayName("revoke된 세션 재연결 → SESSION_INVALIDATED 이벤트")
        void revokedSession_returnsSessionInvalidatedEvent() throws Exception {
            Claims claims = mock(Claims.class);
            given(claims.getIssuedAt()).willReturn(Date.from(Instant.now().minusSeconds(60)));
            given(registry.isRevokedAfter(anyString(), any(Instant.class))).willReturn(true);

            mockMvc.perform(get("/api/sse/session").accept(MediaType.TEXT_EVENT_STREAM)
                    .principal(authWith(claims)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("SESSION_INVALIDATED")));
        }
    }
}
