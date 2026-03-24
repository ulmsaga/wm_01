package com.mobigen.aiop.nttpoc.module.menu.service;

import java.util.List;

import com.mobigen.aiop.nttpoc.module.menu.dto.MenuResponse;

public interface MenuService {
    List<MenuResponse> getMenuTree(long userSeq);
}
