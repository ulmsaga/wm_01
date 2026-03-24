package com.mobigen.aiop.nttpoc.module.menu.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import com.mobigen.aiop.nttpoc.module.menu.dao.MenuDao;
import com.mobigen.aiop.nttpoc.module.menu.dto.MenuResponse;
import com.mobigen.aiop.nttpoc.module.menu.service.MenuService;

@Service
public class MenuServiceImpl implements MenuService {

    private final MenuDao menuDao;

    @Autowired
    public MenuServiceImpl(MenuDao menuDao) {
        this.menuDao = menuDao;
    }

    @Override
    public List<MenuResponse> getMenuTree(long userSeq) {
        List<Map<String, Object>> rows = menuDao.selectMenuByUserSeq(userSeq);

        // 1단계: flat 결과 → 노드 맵 (insertion order 유지 = DB sort_order 순)
        Map<Integer, MenuNode> nodeMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            MenuNode node = new MenuNode(row);
            nodeMap.put(node.menuId, node);
        }

        // 2단계: 부모-자식 연결
        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode node : nodeMap.values()) {
            if (node.parentId == null) {
                roots.add(node);
            } else {
                MenuNode parent = nodeMap.get(node.parentId);
                if (parent != null) {
                    parent.children.add(node);
                }
            }
        }

        // 3단계: sort_order 정렬 후 MenuResponse 변환
        roots.sort(Comparator.comparingInt(n -> n.sortOrder));
        return roots.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private MenuResponse toResponse(MenuNode node) {
        node.children.sort(Comparator.comparingInt(n -> n.sortOrder));
        List<MenuResponse> children = node.children.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        Map<String, Object> r = node.raw;
        String lang = LocaleContextHolder.getLocale().getLanguage();
        String nameKey = switch (lang) {
            case "en" -> "MENU_NAME_EN";
            case "ko" -> "MENU_NAME_KO";
            default   -> "MENU_NAME";
        };
        String menuName = (String) r.get(nameKey);
        if (menuName == null) menuName = (String) r.get("MENU_NAME");
        return new MenuResponse(
                node.menuId,
                node.parentId,
                menuName,
                (String) r.get("MENU_PATH"),
                (String) r.get("ICON"),
                toBool(r.get("CAN_VIEW")),
                toBool(r.get("CAN_EDIT")),
                toBool(r.get("CAN_DELETE")),
                toBool(r.get("CAN_DOWNLOAD")),
                toBool(r.get("CAN_UPLOAD")),
                toBool(r.get("CAN_UNMASK")),
                children
        );
    }

    private static boolean toBool(Object val) {
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.intValue() == 1;
        return false;
    }

    private static class MenuNode {
        final Map<String, Object> raw;
        final int menuId;
        final Integer parentId;
        final int sortOrder;
        final List<MenuNode> children = new ArrayList<>();

        MenuNode(Map<String, Object> raw) {
            this.raw = raw;
            this.menuId = ((Number) raw.get("MENU_ID")).intValue();
            Object pid = raw.get("PARENT_ID");
            this.parentId = pid != null ? ((Number) pid).intValue() : null;
            this.sortOrder = ((Number) raw.get("SORT_ORDER")).intValue();
        }
    }
}
