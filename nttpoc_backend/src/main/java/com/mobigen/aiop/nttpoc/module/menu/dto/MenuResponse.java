package com.mobigen.aiop.nttpoc.module.menu.dto;

import java.util.List;

public record MenuResponse(
        long menuId,
        Long parentId,
        String menuName,
        String menuPath,
        String icon,
        boolean canView,
        boolean canEdit,
        boolean canDelete,
        boolean canDownload,
        boolean canUpload,
        boolean canUnmask,
        List<MenuResponse> children
) {}
