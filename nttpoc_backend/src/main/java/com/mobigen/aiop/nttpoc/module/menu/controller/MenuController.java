package com.mobigen.aiop.nttpoc.module.menu.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mobigen.aiop.nttpoc.core.dto.ApiResponse;
import com.mobigen.aiop.nttpoc.module.menu.dto.MenuResponse;
import com.mobigen.aiop.nttpoc.module.menu.service.MenuService;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuService menuService;

    @Autowired
    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /** 현재 로그인 사용자의 접근 가능 메뉴 트리 조회 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<MenuResponse>>> getMenu(Authentication authentication) {
        long userSeq = Long.parseLong(authentication.getName());
        List<MenuResponse> tree = menuService.getMenuTree(userSeq);
        return ResponseEntity.ok(ApiResponse.ok(tree));
    }
}
