package com.mobigen.aiop.nttpoc.module.menu.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.mobigen.aiop.nttpoc.core.exception.GlobalExceptionHandler;
import com.mobigen.aiop.nttpoc.module.menu.dto.MenuResponse;
import com.mobigen.aiop.nttpoc.module.menu.service.MenuService;

@ExtendWith(MockitoExtension.class)
@DisplayName("MenuController")
class MenuControllerTest {

    @Mock MenuService menuService;
    @Mock MessageSource messageSource;
    @InjectMocks MenuController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GlobalExceptionHandler exHandler = new GlobalExceptionHandler(messageSource);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exHandler)
                .build();
        SecurityContextHolder.clearContext();
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("에러");
    }

    /**
     * Authentication 파라미터 주입 방법:
     * - SecurityContextHolder 설정 → Spring Security 필터가 없는 standaloneSetup에서는 미작동
     * - request.setUserPrincipal(auth) → PrincipalMethodArgumentResolver가 인식
     */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            authenticatedGet(String url, long userSeq) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                String.valueOf(userSeq), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        return get(url).with(request -> {
            request.setUserPrincipal(auth);
            return request;
        });
    }

    private MenuResponse leaf(int id, Integer parentId, String name, String path) {
        return new MenuResponse(id, parentId, name, path, "icon-nw",
                true, false, false, true, false, false, List.of());
    }

    @Nested
    @DisplayName("GET /api/menu")
    class GetMenu {

        @Test
        @DisplayName("인증된 사용자 → 200 + 메뉴 트리 반환")
        void authenticated_returns200WithTree() throws Exception {
            given(menuService.getMenuTree(42L)).willReturn(List.of(
                    new MenuResponse(1, null, "NW", null, "icon-nw",
                            false, false, false, false, false, false,
                            List.of(leaf(3, 1, "NW 감시", "/nw/monitoring")))
            ));

            mockMvc.perform(authenticatedGet("/api/menu", 42L).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].menuName").value("NW"))
                    .andExpect(jsonPath("$.data[0].children[0].menuName").value("NW 감시"))
                    .andExpect(jsonPath("$.data[0].children[0].menuPath").value("/nw/monitoring"));
        }

        @Test
        @DisplayName("메뉴가 없는 경우 → 200 + 빈 배열")
        void noMenu_returns200WithEmptyArray() throws Exception {
            given(menuService.getMenuTree(1L)).willReturn(List.of());

            mockMvc.perform(authenticatedGet("/api/menu", 1L).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("userSeq가 Authentication.getName()에서 올바르게 파싱됨")
        void userSeq_parsedFromAuthName() throws Exception {
            given(menuService.getMenuTree(99L)).willReturn(List.of());

            mockMvc.perform(authenticatedGet("/api/menu", 99L))
                    .andExpect(status().isOk());

            verify(menuService).getMenuTree(99L);
        }
    }
}
