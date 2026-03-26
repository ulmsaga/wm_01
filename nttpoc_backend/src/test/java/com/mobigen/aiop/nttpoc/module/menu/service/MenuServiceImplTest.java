package com.mobigen.aiop.nttpoc.module.menu.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.BDDMockito.given;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mobigen.aiop.nttpoc.module.menu.dao.MenuDao;
import com.mobigen.aiop.nttpoc.module.menu.dto.MenuResponse;
import com.mobigen.aiop.nttpoc.module.menu.service.impl.MenuServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("MenuServiceImpl")
class MenuServiceImplTest {

    @Mock MenuDao menuDao;
    @InjectMocks MenuServiceImpl menuService;

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    /** DB 조회 결과 row 생성 (HashMap — null value 허용, 권한 기본값: canView/canDownload=true) */
    private java.util.Map<String, Object> row(long menuId, Long parentId, String name,
                                              String path, String icon, int sortOrder) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("MENU_ID",      menuId);
        m.put("PARENT_ID",    parentId);
        m.put("MENU_NAME",    name);
        m.put("MENU_NAME_EN", null);
        m.put("MENU_NAME_KO", null);
        m.put("MENU_PATH",    path);
        m.put("ICON",         icon);
        m.put("SORT_ORDER",   sortOrder);
        m.put("CAN_VIEW",     true);
        m.put("CAN_EDIT",     false);
        m.put("CAN_DELETE",   false);
        m.put("CAN_DOWNLOAD", true);
        m.put("CAN_UPLOAD",   false);
        m.put("CAN_UNMASK",   false);
        return m;
    }

    // ── 테스트 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMenuTree")
    class GetMenuTree {

        @Test
        @DisplayName("DB 결과 빈 리스트 → 빈 트리 반환")
        void emptyRows_returnsEmptyList() {
            given(menuDao.selectMenuByUserSeq(1L)).willReturn(List.of());

            List<MenuResponse> result = menuService.getMenuTree(1L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("루트 항목만 있을 때 → 평탄한 리스트 반환")
        void rootOnlyRows_returnsFlatList() {
            given(menuDao.selectMenuByUserSeq(1L)).willReturn(List.of(
                    row(1, null, "NW",  null,        "icon-nw",  1),
                    row(7, null, "APP", null,        "icon-app", 2),
                    row(12, null, "관리", null,       "icon-admin", 3)
            ));

            List<MenuResponse> result = menuService.getMenuTree(1L);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).menuName()).isEqualTo("NW");
            assertThat(result.get(1).menuName()).isEqualTo("APP");
            assertThat(result.get(2).menuName()).isEqualTo("관리");
            result.forEach(r -> assertThat(r.children()).isEmpty());
        }

        @Test
        @DisplayName("부모-자식 구조 → 올바른 트리 조립")
        void nestedRows_buildsCorrectTree() {
            given(menuDao.selectMenuByUserSeq(1L)).willReturn(List.of(
                    row(1,  null, "NW",       null,              "icon-nw",      1),
                    row(2L, 1L,   "감시",     null,              null,            1),
                    row(3L, 2L,   "NW 감시",  "/nw/monitoring",  "icon-monitor", 1)
            ));

            List<MenuResponse> result = menuService.getMenuTree(1L);

            assertThat(result).hasSize(1);
            MenuResponse nw = result.get(0);
            assertThat(nw.menuName()).isEqualTo("NW");
            assertThat(nw.children()).hasSize(1);

            MenuResponse monitoring = nw.children().get(0);
            assertThat(monitoring.menuName()).isEqualTo("감시");
            assertThat(monitoring.children()).hasSize(1);

            MenuResponse nwMonitor = monitoring.children().get(0);
            assertThat(nwMonitor.menuName()).isEqualTo("NW 감시");
            assertThat(nwMonitor.menuPath()).isEqualTo("/nw/monitoring");
            assertThat(nwMonitor.children()).isEmpty();
        }

        @Test
        @DisplayName("sort_order 기준으로 형제 노드 정렬")
        void sortOrder_siblingsOrdered() {
            given(menuDao.selectMenuByUserSeq(1L)).willReturn(List.of(
                    row(1,    null, "NW",       null,           "icon-nw",    1),
                    row(4L,   1L,   "분석",     null,           null,          2),
                    row(2L,   1L,   "감시",     null,           null,          1),
                    row(5L,   4L,   "KPI 분석", "/nw/kpi",     "icon-kpi",    1),
                    row(6L,   4L,   "CAUSE",    "/nw/cause",   "icon-cause",  2)
            ));

            List<MenuResponse> result = menuService.getMenuTree(1L);

            assertThat(result).hasSize(1);
            List<MenuResponse> children = result.get(0).children();
            assertThat(children.get(0).menuName()).isEqualTo("감시");  // sortOrder=1
            assertThat(children.get(1).menuName()).isEqualTo("분석");  // sortOrder=2

            List<MenuResponse> analysisChildren = children.get(1).children();
            assertThat(analysisChildren.get(0).menuName()).isEqualTo("KPI 분석");
            assertThat(analysisChildren.get(1).menuName()).isEqualTo("CAUSE");
        }

        @Test
        @DisplayName("권한 플래그 Boolean 타입 정상 매핑")
        void booleanFlags_mappedCorrectly() {
            java.util.Map<String, Object> r = new java.util.HashMap<>();
            r.put("MENU_ID",    1);
            r.put("PARENT_ID",  null);
            r.put("MENU_NAME",  "NW");
            r.put("MENU_PATH",  null);
            r.put("ICON",       null);
            r.put("SORT_ORDER", 1);
            r.put("CAN_VIEW",     true);
            r.put("CAN_EDIT",     false);
            r.put("CAN_DELETE",   false);
            r.put("CAN_DOWNLOAD", true);
            r.put("CAN_UPLOAD",   false);
            r.put("CAN_UNMASK",   true);
            given(menuDao.selectMenuByUserSeq(1L)).willReturn(List.of(r));

            MenuResponse result = menuService.getMenuTree(1L).get(0);

            assertThat(result.canView()).isTrue();
            assertThat(result.canEdit()).isFalse();
            assertThat(result.canDelete()).isFalse();
            assertThat(result.canDownload()).isTrue();
            assertThat(result.canUpload()).isFalse();
            assertThat(result.canUnmask()).isTrue();
        }

        @Test
        @DisplayName("권한 플래그 Number 타입(tinyint) 정상 매핑")
        void numberFlags_mappedCorrectly() {
            java.util.Map<String, Object> r = new java.util.HashMap<>();
            r.put("MENU_ID",    1);
            r.put("PARENT_ID",  null);
            r.put("MENU_NAME",  "NW");
            r.put("MENU_PATH",  null);
            r.put("ICON",       null);
            r.put("SORT_ORDER", 1);
            r.put("CAN_VIEW",     1);   // Number 타입 (TINYINT)
            r.put("CAN_EDIT",     0);
            r.put("CAN_DELETE",   0);
            r.put("CAN_DOWNLOAD", 1);
            r.put("CAN_UPLOAD",   0);
            r.put("CAN_UNMASK",   0);
            given(menuDao.selectMenuByUserSeq(1L)).willReturn(List.of(r));

            MenuResponse result = menuService.getMenuTree(1L).get(0);

            assertThat(result.canView()).isTrue();
            assertThat(result.canEdit()).isFalse();
            assertThat(result.canDownload()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 parentId 참조 → 고아 노드 제외")
        void orphanNode_excluded() {
            given(menuDao.selectMenuByUserSeq(1L)).willReturn(List.of(
                    row(99L, 999L, "고아노드", "/orphan", null, 1)  // parent 999 없음
            ));

            List<MenuResponse> result = menuService.getMenuTree(1L);

            // parent가 없어서 roots에도 추가 안 됨 → 빈 결과
            assertThat(result).isEmpty();
        }
    }
}
