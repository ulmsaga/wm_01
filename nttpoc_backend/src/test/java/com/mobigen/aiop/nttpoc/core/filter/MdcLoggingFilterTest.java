package com.mobigen.aiop.nttpoc.core.filter;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.BDDMockito.given;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("MdcLoggingFilter")
class MdcLoggingFilterTest {

    @InjectMocks MdcLoggingFilter filter;

    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain chain;

    @Nested
    @DisplayName("doFilterInternal")
    class DoFilterInternal {

        @Test
        @DisplayName("X-Request-Id 헤더 있음 → 기존 값 재사용 + 응답 헤더 설정")
        void existingRequestId_reused() throws Exception {
            given(request.getHeader("X-Request-Id")).willReturn("abc12345");

            filter.doFilterInternal(request, response, chain);

            verify(response).setHeader("X-Request-Id", "abc12345");
            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("X-Request-Id 헤더 없음 → 신규 8자리 requestId 생성")
        void noRequestId_generated() throws Exception {
            given(request.getHeader("X-Request-Id")).willReturn(null);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            // MDC가 필터 완료 후 클리어되었는지 확인
            assertThat(MDC.get("requestId")).isNull();
        }

        @Test
        @DisplayName("빈 X-Request-Id 헤더 → 신규 requestId 생성")
        void blankRequestId_generated() throws Exception {
            given(request.getHeader("X-Request-Id")).willReturn("   ");

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("필터 처리 후 MDC 클리어")
        void mdcClearedAfterFilter() throws Exception {
            given(request.getHeader("X-Request-Id")).willReturn("testid1");

            filter.doFilterInternal(request, response, chain);

            assertThat(MDC.get("requestId")).isNull();
        }
    }
}
